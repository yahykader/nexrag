package com.exemple.nexrag.service.rag.streaming;

import com.exemple.nexrag.service.rag.streaming.model.ConversationState;
import com.exemple.nexrag.service.rag.streaming.model.ConversationState.Message;
import com.exemple.nexrag.service.rag.streaming.model.ConversationState.SourceReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Spec : ConversationManager — Persistance Redis des conversations
 *
 * <p>SRP : teste uniquement la gestion des états de conversation (création, récupération,
 * historique fenêtré, TTL Redis). Aucune connaissance du streaming ni de la génération.
 *
 * <p>Dépendances mockées : RedisTemplate, ValueOperations, ObjectMapper.
 */
@DisplayName("Spec : ConversationManager — Persistance Redis des conversations")
@ExtendWith(MockitoExtension.class)
class ConversationManagerSpec {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ConversationManager manager;

    private static final String USER_ID = "user-test-123";

    @BeforeEach
    void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // =========================================================================
    // T008 — FR-001 / AC-11.1 : Création de conversation
    // =========================================================================

    @Test
    @DisplayName("DOIT créer une conversation avec userId et ID unique au format conv_XXXXXXXXXXXXXXXX")
    void shouldCreateConversationWithUserIdAndUniqueId() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any(ConversationState.class))).thenReturn("{}");

        ConversationState state = manager.createConversation(USER_ID);

        assertThat(state.getConversationId())
                .isNotNull()
                .startsWith("conv_")
                .hasSizeGreaterThanOrEqualTo(21); // "conv_" + 16 hex chars
        assertThat(state.getUserId()).isEqualTo(USER_ID);
        assertThat(state.getMessages()).isEmpty();
        assertThat(state.getCreatedAt()).isNotNull();

        verify(valueOps).set(
                eq("conversation:" + state.getConversationId()),
                eq("{}"),
                eq(3600L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("DOIT générer des identifiants uniques pour deux conversations distinctes")
    void shouldGenerateUniqueConversationIds() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ConversationState first = manager.createConversation(USER_ID);
        ConversationState second = manager.createConversation(USER_ID);

        assertThat(first.getConversationId()).isNotEqualTo(second.getConversationId());
    }

    // =========================================================================
    // T009 — FR-004 / edge case : Conversation inexistante
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner Optional.empty() pour une conversation inexistante dans Redis")
    void shouldReturnEmptyForUnknownConversation() {
        when(valueOps.get("conversation:conv_inexistant")).thenReturn(null);

        Optional<ConversationState> result = manager.getConversation("conv_inexistant");

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // T010 — Résilience : désérialisation JSON échoue
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner Optional.empty() quand la désérialisation JSON échoue")
    void shouldReturnEmptyWhenJsonDeserializationFails() throws JsonProcessingException {
        when(valueOps.get(anyString())).thenReturn("{json-corrompu}");
        when(objectMapper.readValue(eq("{json-corrompu}"), eq(ConversationState.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "json invalide"));

        Optional<ConversationState> result = manager.getConversation("conv_test");

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // T011 — FR-002 : Ajout message utilisateur
    // =========================================================================

    @Test
    @DisplayName("DOIT ajouter un message utilisateur et persister l'état dans Redis")
    void shouldAddUserMessageAndPersistToRedis() throws JsonProcessingException {
        ConversationState existingState = buildConversationState("conv_abc", USER_ID);
        String existingJson = "{}";
        when(valueOps.get("conversation:conv_abc")).thenReturn(existingJson);
        when(objectMapper.readValue(eq(existingJson), eq(ConversationState.class))).thenReturn(existingState);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ConversationState result = manager.addUserMessage("conv_abc", "Quelle est la capitale de la France ?");

        assertThat(result.getMessages()).hasSize(1);
        Message added = result.getMessages().get(0);
        assertThat(added.getRole()).isEqualTo("user");
        assertThat(added.getContent()).isEqualTo("Quelle est la capitale de la France ?");
        assertThat(added.getTimestamp()).isNotNull();

        verify(valueOps).set(eq("conversation:conv_abc"), anyString(), eq(3600L), eq(TimeUnit.SECONDS));
    }

    // =========================================================================
    // T012 — FR-002 : Ajout message assistant avec sources
    // =========================================================================

    @Test
    @DisplayName("DOIT ajouter un message assistant avec sources et mettre à jour le contexte")
    void shouldAddAssistantMessageWithSourcesAndUpdateContext() throws JsonProcessingException {
        ConversationState existingState = buildConversationState("conv_xyz", USER_ID);
        when(valueOps.get("conversation:conv_xyz")).thenReturn("{}");
        when(objectMapper.readValue(eq("{}"), eq(ConversationState.class))).thenReturn(existingState);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        List<SourceReference> sources = List.of(
                SourceReference.builder().file("doc.pdf").relevance(0.9).build()
        );

        ConversationState result = manager.addAssistantMessage("conv_xyz", "Paris est la capitale.", sources, Map.of("tokens", 42));

        assertThat(result.getMessages()).hasSize(1);
        Message added = result.getMessages().get(0);
        assertThat(added.getRole()).isEqualTo("assistant");
        assertThat(added.getContent()).isEqualTo("Paris est la capitale.");
        assertThat(result.getContext()).hasSize(1);
        assertThat(result.getContext().get(0).getDocId()).isEqualTo("doc.pdf");
    }

    // =========================================================================
    // T013 — FR-003 / SC-007 : Troncature de l'historique à MAX_MESSAGES (100)
    // =========================================================================

    @Test
    @DisplayName("DOIT tronquer le message le plus ancien quand l'historique dépasse MAX_MESSAGES (100)")
    void shouldTruncateOldestMessageWhenMaxMessagesExceeded() throws JsonProcessingException {
        ConversationState stateWith100Messages = buildConversationState("conv_full", USER_ID);
        for (int i = 0; i < 100; i++) {
            stateWith100Messages.getMessages().add(Message.builder()
                    .role("user")
                    .content("Message numéro " + i)
                    .timestamp(Instant.now())
                    .metadata(new HashMap<>())
                    .build());
        }
        when(valueOps.get("conversation:conv_full")).thenReturn("{}");
        when(objectMapper.readValue(eq("{}"), eq(ConversationState.class))).thenReturn(stateWith100Messages);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ConversationState result = manager.addUserMessage("conv_full", "Message numéro 100 (101ème ajout)");

        assertThat(result.getMessages()).hasSize(100);
        assertThat(result.getMessages().get(0).getContent()).isEqualTo("Message numéro 1");
        assertThat(result.getMessages().get(99).getContent()).isEqualTo("Message numéro 100 (101ème ajout)");
    }

    // =========================================================================
    // T014 — Edge case : IllegalArgumentException pour conversationId introuvable
    // =========================================================================

    @Test
    @DisplayName("DOIT lever IllegalArgumentException quand conversationId est introuvable dans addUserMessage")
    void shouldThrowIllegalArgumentExceptionForUnknownConversationOnAddMessage() {
        when(valueOps.get("conversation:conv_inconnu")).thenReturn(null);

        assertThatThrownBy(() -> manager.addUserMessage("conv_inconnu", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conv_inconnu");
    }

    @Test
    @DisplayName("DOIT lever IllegalArgumentException quand conversationId est introuvable dans addAssistantMessage")
    void shouldThrowIllegalArgumentExceptionForUnknownConversationOnAddAssistantMessage() {
        when(valueOps.get("conversation:conv_inconnu")).thenReturn(null);

        assertThatThrownBy(() -> manager.addAssistantMessage("conv_inconnu", "réponse", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conv_inconnu");
    }

    // =========================================================================
    // T015 — FR-004 : Suppression de conversation
    // =========================================================================

    @Test
    @DisplayName("DOIT supprimer la conversation de Redis via deleteConversation")
    void shouldDeleteConversationFromRedis() {
        manager.deleteConversation("conv_del");

        verify(redisTemplate).delete("conversation:conv_del");
    }

    // =========================================================================
    // T016 — FR-005 : Renouvellement du TTL
    // =========================================================================

    @Test
    @DisplayName("DOIT renouveler le TTL sans modifier le contenu via refreshTTL")
    void shouldRefreshTtlWithoutModifyingContent() {
        manager.refreshTTL("conv_refresh");

        verify(redisTemplate).expire("conversation:conv_refresh", 3600, TimeUnit.SECONDS);
        verifyNoMoreInteractions(valueOps);
    }

    // =========================================================================
    // T017 — FR-002 : getMessageHistory retourne les N derniers messages
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner les N derniers messages avec getMessageHistory")
    void shouldReturnLastNMessagesWithGetMessageHistory() throws JsonProcessingException {
        ConversationState state = buildConversationState("conv_hist", USER_ID);
        for (int i = 0; i < 10; i++) {
            state.getMessages().add(Message.builder()
                    .role("user").content("msg-" + i).timestamp(Instant.now()).metadata(new HashMap<>()).build());
        }
        when(valueOps.get("conversation:conv_hist")).thenReturn("{}");
        when(objectMapper.readValue(eq("{}"), eq(ConversationState.class))).thenReturn(state);

        List<Message> history = manager.getMessageHistory("conv_hist", 3);

        assertThat(history).hasSize(3);
        assertThat(history.get(0).getContent()).isEqualTo("msg-7");
        assertThat(history.get(2).getContent()).isEqualTo("msg-9");
    }

    @Test
    @DisplayName("DOIT retourner une liste vide pour une conversation inexistante dans getMessageHistory")
    void shouldReturnEmptyListForUnknownConversationInGetMessageHistory() {
        when(valueOps.get(anyString())).thenReturn(null);

        List<Message> history = manager.getMessageHistory("conv_inconnu", 10);

        assertThat(history).isEmpty();
    }

    // =========================================================================
    // T018 — FR-002 : enrichQueryWithContext enrichit avec les 3 derniers messages
    // =========================================================================

    @Test
    @DisplayName("DOIT enrichir la query avec les 3 derniers messages de l'historique")
    void shouldEnrichQueryWithLastThreeMessages() throws JsonProcessingException {
        ConversationState state = buildConversationState("conv_enrich", USER_ID);
        for (int i = 0; i < 5; i++) {
            state.getMessages().add(Message.builder()
                    .role("user").content("question-" + i).timestamp(Instant.now()).metadata(new HashMap<>()).build());
        }
        when(valueOps.get("conversation:conv_enrich")).thenReturn("{}");
        when(objectMapper.readValue(eq("{}"), eq(ConversationState.class))).thenReturn(state);

        String enriched = manager.enrichQueryWithContext("conv_enrich", "Nouvelle question");

        assertThat(enriched)
                .contains("Contexte de la conversation")
                .contains("question-2")
                .contains("question-3")
                .contains("question-4")
                .contains("Nouvelle question");
    }

    // =========================================================================
    // T019 — Edge case : enrichQueryWithContext retourne query inchangée si conversation absente
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner la query inchangée quand la conversation est inexistante dans enrichQueryWithContext")
    void shouldReturnUnchangedQueryForUnknownConversationInEnrich() {
        when(valueOps.get(anyString())).thenReturn(null);

        String result = manager.enrichQueryWithContext("conv_inconnu", "Ma question");

        assertThat(result).isEqualTo("Ma question");
    }

    @Test
    @DisplayName("DOIT retourner la query inchangée quand l'historique est vide dans enrichQueryWithContext")
    void shouldReturnUnchangedQueryForEmptyHistory() throws JsonProcessingException {
        ConversationState emptyState = buildConversationState("conv_empty", USER_ID);
        when(valueOps.get("conversation:conv_empty")).thenReturn("{}");
        when(objectMapper.readValue(eq("{}"), eq(ConversationState.class))).thenReturn(emptyState);

        String result = manager.enrichQueryWithContext("conv_empty", "Ma question");

        assertThat(result).isEqualTo("Ma question");
    }

    // =========================================================================
    // T020 — [GAP R-06#2] Test documentant l'absence de contrôle userId dans getConversation()
    // =========================================================================

    @Test
    @Disabled("Gap identifié R-06#2 : getConversation() ne vérifie pas le userId propriétaire — " +
              "FR-004 requiert que l'accès soit restreint au propriétaire. " +
              "Corriger ConversationManager.getConversation() pour accepter un userId et rejeter l'accès non autorisé.")
    @DisplayName("[GAP] DOIT_ECHOUER — getConversation() permet l'accès sans vérification du userId propriétaire")
    void shouldRejectAccessFromNonOwnerUserId() throws JsonProcessingException {
        // Scénario : userB tente d'accéder à la conversation de userA
        ConversationState userAConversation = buildConversationState("conv_userA", "userA");
        when(valueOps.get("conversation:conv_userA")).thenReturn("{}");
        when(objectMapper.readValue(eq("{}"), eq(ConversationState.class))).thenReturn(userAConversation);

        // L'API actuelle ne prend pas de userId — gap documenté
        // Comportement ATTENDU (spec Q4) : lever une exception d'accès refusé
        // Comportement ACTUEL : retourne la conversation sans vérification
        Optional<ConversationState> result = manager.getConversation("conv_userA");

        // Ce test doit ÉCHOUER une fois le gap corrigé (getConversation devra prendre un userId)
        assertThat(result).isPresent(); // comportement actuel (à corriger)
    }

    // =========================================================================
    // MÉTHODES UTILITAIRES PRIVÉES
    // =========================================================================

    private ConversationState buildConversationState(String conversationId, String userId) {
        return ConversationState.builder()
                .conversationId(conversationId)
                .userId(userId)
                .createdAt(Instant.now())
                .lastActivity(Instant.now())
                .messages(new ArrayList<>())
                .context(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();
    }
}
