package com.exemple.nexrag.service.rag.retrieval;

import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import com.exemple.nexrag.service.rag.retrieval.RetrievalAugmentorOrchestrator.RetrievalAugmentorResult;
import com.exemple.nexrag.service.rag.retrieval.aggregator.ContentAggregatorService;
import com.exemple.nexrag.service.rag.retrieval.injector.ContentInjectorService;
import com.exemple.nexrag.service.rag.retrieval.model.*;
import com.exemple.nexrag.service.rag.retrieval.query.QueryRouterService;
import com.exemple.nexrag.service.rag.retrieval.query.QueryTransformerService;
import com.exemple.nexrag.service.rag.retrieval.retriever.ParallelRetrieverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import org.mockito.InOrder;
import static org.mockito.Mockito.*;

/**
 * Spec : RetrievalAugmentorOrchestrator — Pipeline complet de retrieval
 *
 * Scope : vérifie l'enchaînement des 5 étapes, l'enregistrement des métriques
 * et la gestion d'erreur (success=false).
 * La correction de chaque étape est couverte par les specs individuels.
 */
@DisplayName("Spec : RetrievalAugmentorOrchestrator — Orchestration du pipeline retrieval")
@ExtendWith(MockitoExtension.class)
class RetrievalAugmentorOrchestratorSpec {

    @Mock
    private QueryTransformerService queryTransformer;
    @Mock
    private QueryRouterService queryRouter;
    @Mock
    private ParallelRetrieverService parallelRetriever;
    @Mock
    private ContentAggregatorService contentAggregator;
    @Mock
    private ContentInjectorService contentInjector;
    @Mock
    private RAGMetrics ragMetrics;

    private RetrievalAugmentorOrchestrator orchestrator;

    // Résultats mocks pour les étapes du pipeline
    private QueryTransformResult mockTransformResult;
    private RoutingDecision mockRoutingDecision;
    private Map<String, RetrievalResult> mockRetrievalResults;
    private AggregatedContext mockAggregatedContext;
    private InjectedPrompt mockInjectedPrompt;

    private static final String TEST_QUERY = "quels sont les résultats du trimestre ?";
    private static final String EXPECTED_PROMPT = "Prompt final avec contexte injecté";

    @BeforeEach
    void setUp() {
        orchestrator = new RetrievalAugmentorOrchestrator(
            queryTransformer, queryRouter, parallelRetriever,
            contentAggregator, contentInjector, ragMetrics
        );

        // Étape 1 — QueryTransformer
        mockTransformResult = QueryTransformResult.builder()
            .originalQuery(TEST_QUERY)
            .variants(List.of(TEST_QUERY, "résultats trimestriels", "performance Q1"))
            .method("rule-based")
            .durationMs(5)
            .confidence(0.85)
            .build();

        // Étape 2 — QueryRouter
        mockRoutingDecision = RoutingDecision.builder()
            .strategy(RoutingDecision.Strategy.HYBRID)
            .confidence(0.7)
            .retrievers(Map.of(
                "text", RoutingDecision.RetrieverConfig.builder()
                    .enabled(true).topK(20).priority(RoutingDecision.Priority.HIGH).estimatedLatencyMs(50).build()
            ))
            .estimatedTotalDurationMs(50)
            .parallelExecution(true)
            .build();

        // Étape 3 — ParallelRetriever
        mockRetrievalResults = Map.of(
            "text", RetrievalTestHelper.buildRetrievalResult("text",
                List.of(RetrievalTestHelper.buildScoredChunk("c1", "résultats trimestriels positifs", 0.9, "text")))
        );

        // Étape 4 — ContentAggregator
        mockAggregatedContext = RetrievalTestHelper.buildAggregatedContext(
            List.of(RetrievalTestHelper.buildSelectedChunk("c1", "résultats positifs", 0.05, "text"))
        );

        // Étape 5 — ContentInjector
        mockInjectedPrompt = InjectedPrompt.builder()
            .fullPrompt(EXPECTED_PROMPT)
            .structure(InjectedPrompt.PromptStructure.builder()
                .systemPrompt("system")
                .documentsContext("docs")
                .userQuery(TEST_QUERY)
                .instructions("instructions")
                .totalTokens(500)
                .build())
            .sources(List.of())
            .contextUsagePercent(0.25)
            .durationMs(3)
            .build();
    }

    private void stubPipelineHappyPath() {
        when(queryTransformer.transform(TEST_QUERY)).thenReturn(mockTransformResult);
        when(queryRouter.route(TEST_QUERY)).thenReturn(mockRoutingDecision);
        when(parallelRetriever.retrieveParallel(anyList(), any())).thenReturn(mockRetrievalResults);
        when(contentAggregator.aggregate(any(), anyString())).thenReturn(mockAggregatedContext);
        when(contentInjector.injectContext(any(), anyString())).thenReturn(mockInjectedPrompt);
    }

    // =========================================================================
    // Enchaînement des 5 étapes
    // =========================================================================

    @Test
    @DisplayName("DOIT appeler les 5 étapes en séquence et retourner success=true")
    void doitAppeler5EtapesEnSequenceEtRetournerSuccessTrue() {
        // Given
        stubPipelineHappyPath();

        // When
        RetrievalAugmentorResult result = orchestrator.execute(TEST_QUERY);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOriginalQuery()).isEqualTo(TEST_QUERY);
        assertThat(result.getTransformResult()).isEqualTo(mockTransformResult);
        assertThat(result.getRoutingDecision()).isEqualTo(mockRoutingDecision);
        assertThat(result.getRetrievalResults()).isEqualTo(mockRetrievalResults);
        assertThat(result.getAggregatedContext()).isEqualTo(mockAggregatedContext);
        assertThat(result.getInjectedPrompt()).isEqualTo(mockInjectedPrompt);

        // Vérifier ordre d'appel
        InOrder inOrder = inOrder(queryTransformer, queryRouter, parallelRetriever, contentAggregator, contentInjector);
        inOrder.verify(queryTransformer).transform(TEST_QUERY);
        inOrder.verify(queryRouter).route(TEST_QUERY);
        inOrder.verify(parallelRetriever).retrieveParallel(anyList(), any());
        inOrder.verify(contentAggregator).aggregate(any(), anyString());
        inOrder.verify(contentInjector).injectContext(any(), anyString());
    }

    // =========================================================================
    // Enregistrement des métriques RAGMetrics
    // =========================================================================

    @Test
    @DisplayName("DOIT enregistrer les métriques RAGMetrics pour chaque étape du pipeline")
    void doitEnregistrerMetriquesRAGMetricsPourChaqueEtape() {
        // Given
        stubPipelineHappyPath();

        // When
        orchestrator.execute(TEST_QUERY);

        // Then — métriques enregistrées pour transformation, routing, retrieval, agrégation
        verify(ragMetrics).recordQueryTransformation(anyLong(), anyInt());
        verify(ragMetrics).recordRoutingDecision(anyString(), anyDouble());
        verify(ragMetrics, atLeastOnce()).recordRetrieval(anyString(), anyLong(), anyInt());
        verify(ragMetrics).recordAggregation(anyLong(), anyInt(), anyInt());
    }

    // =========================================================================
    // Gestion d'erreur — success=false sans exception propagée
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner success=false sans lever d'exception quand une étape lève une RuntimeException")
    void doitRetournerSuccessFalseSansExceptionQuandEtapePlante() {
        // Given — queryTransformer plante
        when(queryTransformer.transform(TEST_QUERY))
            .thenThrow(new RuntimeException("Erreur transformation"));

        // When — pas d'exception levée
        RetrievalAugmentorResult result = orchestrator.execute(TEST_QUERY);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
        assertThat(result.getErrorMessage()).contains("Erreur transformation");
        assertThat(result.getOriginalQuery()).isEqualTo(TEST_QUERY);
    }

    // =========================================================================
    // Shortcut getFinalPrompt()
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner le prompt de l'étape 5 via result.getFinalPrompt()")
    void doitRetournerPromptEtape5ViaGetFinalPrompt() {
        // Given
        stubPipelineHappyPath();

        // When
        RetrievalAugmentorResult result = orchestrator.execute(TEST_QUERY);

        // Then
        assertThat(result.getFinalPrompt()).isEqualTo(EXPECTED_PROMPT);
        assertThat(result.isSuccess()).isTrue();
    }
}
