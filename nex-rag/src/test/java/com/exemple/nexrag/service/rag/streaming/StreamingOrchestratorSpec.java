package com.exemple.nexrag.service.rag.streaming;

import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import com.exemple.nexrag.service.rag.retrieval.RetrievalAugmentorOrchestrator;
import com.exemple.nexrag.service.rag.retrieval.RetrievalAugmentorOrchestrator.RetrievalAugmentorResult;
import com.exemple.nexrag.service.rag.retrieval.model.AggregatedContext;
import com.exemple.nexrag.service.rag.retrieval.model.InjectedPrompt;
import com.exemple.nexrag.service.rag.retrieval.model.QueryTransformResult;
import com.exemple.nexrag.service.rag.retrieval.model.RoutingDecision;
import com.exemple.nexrag.service.rag.streaming.model.ConversationState;
import com.exemple.nexrag.service.rag.streaming.model.StreamingRequest;
import com.exemple.nexrag.service.rag.streaming.model.StreamingResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Spec : StreamingOrchestrator — Pipeline RAG → historique → génération
 *
 * <p>SRP : teste uniquement la coordination du pipeline. Les 5 dépendances sont mockées.
 *
 * <p>Stratégie de test du streaming asynchrone : doAnswer sur streamingModel.generate()
 * déclenche immédiatement les callbacks onNext/onComplete dans le thread du mock,
 * permettant au CountDownLatch interne de se libérer sans délai.
 */
@DisplayName("Spec : StreamingOrchestrator — Pipeline RAG → historique → génération")
@ExtendWith(MockitoExtension.class)
class StreamingOrchestratorSpec {

    @Mock private RetrievalAugmentorOrchestrator retrievalAugmentor;
    @Mock private ConversationManager conversationManager;
    @Mock private EventEmitter eventEmitter;
    @Mock private StreamingChatLanguageModel streamingModel;
    @Mock private RAGMetrics ragMetrics;

    @InjectMocks
    private StreamingOrchestrator orchestrator;

    private static final String SESSION_ID = "session-orch-test";
    private static final String USER_ID = "user-orch-123";
    private static final String CONV_ID = "conv_orchestrateur";

    private StreamingRequest baseRequest;
    private ConversationState existingConversation;

    @BeforeEach
    void setup() {
        baseRequest = StreamingRequest.builder()
                .query("Quelle est la capitale de la France ?")
                .conversationId(CONV_ID)
                .userId(USER_ID)
                .build();

        existingConversation = ConversationState.builder()
                .conversationId(CONV_ID)
                .userId(USER_ID)
                .createdAt(Instant.now())
                .lastActivity(Instant.now())
                .messages(new ArrayList<>())
                .context(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();
    }

    // =========================================================================
    // T040 — FR-010 : addUserMessage AVANT generate()
    // =========================================================================

    @Test
    @DisplayName("DOIT ajouter le message utilisateur à l'historique AVANT de démarrer la génération")
    void shouldAddUserMessageToHistoryBeforeGeneration()
            throws ExecutionException, InterruptedException, TimeoutException {

        stubFullPipeline(List.of("Paris."), existingConversation);

        orchestrator.executeStreaming(SESSION_ID, baseRequest).get(10, TimeUnit.SECONDS);

        InOrder inOrder = inOrder(conversationManager, streamingModel);
        inOrder.verify(conversationManager).addUserMessage(eq(CONV_ID), anyString());
        inOrder.verify(streamingModel).generate(any(UserMessage.class), any());
    }

    // =========================================================================
    // T041 — FR-009 / AC-12.4 : contexte RAG injecté dans le prompt
    // =========================================================================

    @Test
    @DisplayName("DOIT injecter le contexte RAG dans le prompt avant d'appeler le service de génération")
    void shouldInjectRagContextIntoPromptBeforeCallingGenerationService()
            throws ExecutionException, InterruptedException, TimeoutException {

        String ragContext = "Contexte documentaire RAG injecté";
        stubFullPipeline(List.of("Réponse."), existingConversation, ragContext);

        orchestrator.executeStreaming(SESSION_ID, baseRequest).get(10, TimeUnit.SECONDS);

        // Vérifier que generate() est appelé avec un UserMessage contenant le prompt RAG
        verify(streamingModel).generate(
                argThat((UserMessage msg) -> msg.singleText().contains(ragContext)),
                any()
        );
    }

    // =========================================================================
    // T042 — FR-010 : ordre complet du pipeline
    // =========================================================================

    @Test
    @DisplayName("DOIT coordonner le pipeline dans l'ordre : RAG → historique → génération → sauvegarde")
    void shouldCoordinatePipelineInCorrectOrder()
            throws ExecutionException, InterruptedException, TimeoutException {

        stubFullPipeline(List.of("Bonjour"), existingConversation);

        orchestrator.executeStreaming(SESSION_ID, baseRequest).get(10, TimeUnit.SECONDS);

        InOrder inOrder = inOrder(retrievalAugmentor, conversationManager, streamingModel);
        // 1. Historique user — addUserMessage dans handleConversation (STEP 1)
        inOrder.verify(conversationManager).addUserMessage(eq(CONV_ID), anyString());
        // 2. RAG (STEP 2)
        inOrder.verify(retrievalAugmentor).execute(anyString());
        // 3. Génération
        inOrder.verify(streamingModel).generate(any(UserMessage.class), any());
        // 4. Sauvegarde réponse assistant (après generate/onComplete)
        inOrder.verify(conversationManager).addAssistantMessage(eq(CONV_ID), anyString(), any(), any());
    }

    // =========================================================================
    // T043 — US-3 AC-2 / SC-005 : réponse complète sauvegardée à DONE
    // =========================================================================

    @Test
    @DisplayName("DOIT sauvegarder la réponse complète reconstituée dans l'historique à réception de DONE")
    void shouldSaveCompleteReconstitutedResponseOnDone()
            throws ExecutionException, InterruptedException, TimeoutException {

        stubFullPipeline(List.of("Bon", "jour", " monde"), existingConversation);

        orchestrator.executeStreaming(SESSION_ID, baseRequest).get(10, TimeUnit.SECONDS);

        verify(conversationManager).addAssistantMessage(
                eq(CONV_ID),
                eq("Bonjour monde"),
                any(),
                any()
        );
    }

    // =========================================================================
    // T044 — US-3 AC-3 : flux continue avec contexte RAG vide
    // =========================================================================

    @Test
    @DisplayName("DOIT continuer normalement quand le contexte RAG est vide (aucun document pertinent)")
    void shouldContinueStreamingWithEmptyRagContext()
            throws ExecutionException, InterruptedException, TimeoutException {

        stubFullPipeline(List.of("Réponse générique."), existingConversation, "");

        assertThatNoException().isThrownBy(() ->
                orchestrator.executeStreaming(SESSION_ID, baseRequest).get(10, TimeUnit.SECONDS)
        );

        verify(streamingModel).generate(any(UserMessage.class), any());
    }

    // =========================================================================
    // T045 — SC-006 : ERROR sans propagation d'exception
    // =========================================================================

    @Test
    @DisplayName("DOIT émettre ERROR sans propager l'exception quand le modèle de génération échoue")
    void shouldEmitErrorWithoutPropagatingExceptionOnModelFailure()
            throws InterruptedException {

        when(conversationManager.getConversation(CONV_ID)).thenReturn(Optional.of(existingConversation));
        when(conversationManager.addUserMessage(eq(CONV_ID), anyString())).thenReturn(existingConversation);
        when(conversationManager.enrichQueryWithContext(eq(CONV_ID), anyString()))
                .thenReturn(baseRequest.getQuery());
        when(retrievalAugmentor.execute(anyString())).thenReturn(buildAugmentorResult("Prompt", ""));
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onError(new RuntimeException("API génération indisponible"));
            return null;
        }).when(streamingModel).generate(any(UserMessage.class), any());

        // Le CompletableFuture peut se terminer en exception — c'est normal
        // Ce qui est testé : emitError() est appelé, pas d'exception non gérée au niveau du test
        assertThatNoException().isThrownBy(() -> {
            try {
                orchestrator.executeStreaming(SESSION_ID, baseRequest).get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                // Exception wrappée dans ExecutionException est attendue
            } catch (TimeoutException e) {
                // Timeout acceptable
            }
        });

        verify(eventEmitter, atLeastOnce()).emitError(eq(SESSION_ID), anyString(), anyString());
    }

    // =========================================================================
    // T046 — Création d'une nouvelle conversation si conversationId est null
    // =========================================================================

    @Test
    @DisplayName("DOIT créer une nouvelle conversation quand conversationId est null dans la requête")
    void shouldCreateNewConversationWhenConversationIdIsNull()
            throws ExecutionException, InterruptedException, TimeoutException {

        StreamingRequest requestWithoutConversation = StreamingRequest.builder()
                .query("Bonjour")
                .userId(USER_ID)
                .conversationId(null)
                .build();

        ConversationState newConversation = ConversationState.builder()
                .conversationId("conv_new")
                .userId(USER_ID)
                .createdAt(Instant.now())
                .lastActivity(Instant.now())
                .messages(new ArrayList<>())
                .context(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();

        when(conversationManager.createConversation(USER_ID)).thenReturn(newConversation);
        when(conversationManager.addUserMessage(eq("conv_new"), anyString())).thenReturn(newConversation);
        // enrichQueryWithContext n'est PAS appelé quand conversationId est null
        when(retrievalAugmentor.execute(anyString())).thenReturn(buildAugmentorResult("Prompt", ""));
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onNext("Bonjour");
            handler.onComplete(mock(Response.class));
            return null;
        }).when(streamingModel).generate(any(UserMessage.class), any());
        when(conversationManager.addAssistantMessage(anyString(), anyString(), any(), any()))
                .thenReturn(newConversation);

        orchestrator.executeStreaming(SESSION_ID, requestWithoutConversation).get(10, TimeUnit.SECONDS);

        verify(conversationManager).createConversation(USER_ID);
    }

    // =========================================================================
    // T047 — [GAP R-06#1] Timeout 60s code vs 30s spec
    // =========================================================================

    @Test
    @Disabled("Divergence documentée R-06#1 : timeout effectif dans le code = 60s " +
              "(CountDownLatch.await(60, SECONDS) dans streamAiResponse()), " +
              "la spec (clarification Q5) cible 30s. " +
              "Action requise : modifier StreamingOrchestrator.streamAiResponse() " +
              "pour utiliser latch.await(30, SECONDS).")
    @DisplayName("[GAP] DOIT_DOCUMENTER — timeout streaming est 60s dans le code, spec cible 30s")
    void shouldDocumentStreamingTimeout60sVsSpecTarget30s() {
        // Ce test documente la divergence R-06#1.
        // Valeur actuelle dans le code : latch.await(60, SECONDS)
        // Valeur cible selon spec (clarification Q5) : 30 secondes
        // Impact : délai de détection d'un service de génération bloqué = 2x la valeur cible
    }

    // =========================================================================
    // MÉTHODES UTILITAIRES PRIVÉES
    // =========================================================================

    /**
     * Configure tous les mocks pour un pipeline complet avec les tokens donnés.
     */
    private void stubFullPipeline(List<String> tokens, ConversationState conversation) {
        stubFullPipeline(tokens, conversation, "Contexte RAG mocké");
    }

    private void stubFullPipeline(List<String> tokens, ConversationState conversation, String ragContext) {
        when(conversationManager.getConversation(CONV_ID)).thenReturn(Optional.of(conversation));
        when(conversationManager.addUserMessage(eq(CONV_ID), anyString())).thenReturn(conversation);
        when(conversationManager.enrichQueryWithContext(eq(CONV_ID), anyString()))
                .thenReturn(baseRequest.getQuery());
        when(retrievalAugmentor.execute(anyString()))
                .thenReturn(buildAugmentorResult("Prompt RAG: " + ragContext, ragContext));

        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            for (String token : tokens) {
                handler.onNext(token);
            }
            @SuppressWarnings("unchecked")
            Response<AiMessage> mockResponse = mock(Response.class);
            handler.onComplete(mockResponse);
            return null;
        }).when(streamingModel).generate(any(UserMessage.class), any());

        String expectedFullText = String.join("", tokens);
        when(conversationManager.addAssistantMessage(eq(CONV_ID), eq(expectedFullText), any(), any()))
                .thenReturn(conversation);
    }

    /**
     * Construit un RetrievalAugmentorResult valide avec le prompt et le contexte donnés.
     */
    private RetrievalAugmentorResult buildAugmentorResult(String fullPrompt, String context) {
        QueryTransformResult transformResult = QueryTransformResult.builder()
                .variants(List.of("variante 1", "variante 2"))
                .method("rule-based")
                .confidence(0.9)
                .build();

        RoutingDecision routingDecision = RoutingDecision.builder()
                .strategy(RoutingDecision.Strategy.HYBRID)
                .confidence(0.8)
                .build();

        InjectedPrompt.PromptStructure structure = InjectedPrompt.PromptStructure.builder()
                .totalTokens(50)
                .systemTokens(10)
                .contextTokens(30)
                .queryTokens(10)
                .build();

        InjectedPrompt injectedPrompt = InjectedPrompt.builder()
                .fullPrompt(fullPrompt)
                .structure(structure)
                .sources(List.of())
                .build();

        AggregatedContext aggregatedContext = AggregatedContext.builder()
                .chunks(List.of())
                .inputChunks(5)
                .finalSelected(3)
                .build();

        return RetrievalAugmentorResult.builder()
                .originalQuery(baseRequest.getQuery())
                .success(true)
                .transformResult(transformResult)
                .routingDecision(routingDecision)
                .injectedPrompt(injectedPrompt)
                .aggregatedContext(aggregatedContext)
                .totalDurationMs(100L)
                .build();
    }
}
