package com.exemple.nexrag.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec unitaire de {@link WebSocketAssistantController}.
 *
 * Principe SRP  : valide uniquement le routage des messages RAG (init, query,
 *                 cancel) et le cycle de vie de session via le SessionManager.
 * Principe DIP  : {@link WebSocketSessionManager} et {@link WebSocketSession}
 *                 injectés comme mocks — aucun serveur WebSocket réel.
 * Clean code    : appel direct de {@code handleTextMessage} (méthode protégée,
 *                 accessible dans le même package) au lieu d'un contexte Spring.
 *
 * @see WebSocketAssistantController
 */
@DisplayName("Spec : WebSocketAssistantController — Routage des messages RAG assistant")
@ExtendWith(MockitoExtension.class)
class WebSocketAssistantControllerSpec {

    @Mock
    private WebSocketSessionManager sessionManager;

    @Mock
    private WebSocketSession session;

    private ObjectMapper objectMapper;
    private WebSocketAssistantController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller   = new WebSocketAssistantController(objectMapper, sessionManager);
        when(session.getId()).thenReturn("session-abc");
    }

    // =========================================================================
    // US-27 / AC-27.1 — Connexion : session enregistrée + message "connected"
    // =========================================================================

    @Test
    @DisplayName("DOIT enregistrer la session comme 'anonymous' et envoyer le message connected")
    void devraitEnregistrerSessionAnonymousEtEnvoyerConnected() throws Exception {
        controller.afterConnectionEstablished(session);

        verify(sessionManager).registerSession(session, "anonymous");

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        Map<?, ?> msg = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
        assertThat(msg.get("type")).isEqualTo("connected");
        assertThat(msg.get("sessionId")).isEqualTo("session-abc");
        assertThat(msg.get("timestamp")).isNotNull();
    }

    // =========================================================================
    // US-27 / AC-27.2 — Déconnexion : session désenregistrée
    // =========================================================================

    @Test
    @DisplayName("DOIT désenregistrer la session à la déconnexion")
    void devraitDesenregistrerSessionALaDeconnexion() throws Exception {
        controller.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(sessionManager).unregisterSession("session-abc");
    }

    // =========================================================================
    // US-27 / AC-27.3 — Erreur transport : session désenregistrée
    // =========================================================================

    @Test
    @DisplayName("DOIT désenregistrer la session en cas d'erreur transport")
    void devraitDesenregistrerSessionEnCasErreurTransport() throws Exception {
        controller.handleTransportError(session, new RuntimeException("Erreur réseau"));

        verify(sessionManager).unregisterSession("session-abc");
    }

    // =========================================================================
    // US-27 / AC-27.4 — Message "init" → conversation créée + "conversation_created"
    // =========================================================================

    @Test
    @DisplayName("DOIT créer une conversation et envoyer conversation_created sur message init")
    void devraitCreerConversationSurMessageInit() throws Exception {
        controller.handleTextMessage(session,
            new TextMessage("{\"type\":\"init\",\"userId\":\"user-99\"}"));

        verify(sessionManager).setConversationId(
            eq("session-abc"),
            argThat(id -> id.startsWith("conv_")));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        Map<?, ?> msg = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
        assertThat(msg.get("type")).isEqualTo("conversation_created");
        assertThat(msg.get("userId")).isEqualTo("user-99");
        assertThat(msg.get("conversationId").toString()).startsWith("conv_");
    }

    // =========================================================================
    // US-27 / AC-27.5 — Message "query" avec texte valide → "query_received"
    // =========================================================================

    @Test
    @DisplayName("DOIT envoyer query_received avec la bonne query et le conversationId")
    void devraitEnvoyerQueryReceivedPourQueryValide() throws Exception {
        when(sessionManager.getConversationId("session-abc")).thenReturn("conv-abc123");

        controller.handleTextMessage(session,
            new TextMessage("{\"type\":\"query\",\"text\":\"Quelle est la capitale ?\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        Map<?, ?> msg = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
        assertThat(msg.get("type")).isEqualTo("query_received");
        assertThat(msg.get("query")).isEqualTo("Quelle est la capitale ?");
        assertThat(msg.get("conversationId")).isEqualTo("conv-abc123");
    }

    // =========================================================================
    // US-27 / AC-27.6 — Message "query" sans texte → erreur MISSING_QUERY
    // =========================================================================

    @Test
    @DisplayName("DOIT envoyer l'erreur MISSING_QUERY pour un message query sans champ text")
    void devraitEnvoyerErreurMissingQuerySansChampText() throws Exception {
        controller.handleTextMessage(session, new TextMessage("{\"type\":\"query\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        Map<?, ?> response = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
        assertThat(response.get("type")).isEqualTo("error");
        assertThat(((Map<?, ?>) response.get("error")).get("code")).isEqualTo("MISSING_QUERY");
    }

    // =========================================================================
    // US-27 / AC-27.7 — Message "query" avec texte blanc → erreur MISSING_QUERY
    // =========================================================================

    @Test
    @DisplayName("DOIT envoyer l'erreur MISSING_QUERY pour un message query avec texte blanc")
    void devraitEnvoyerErreurMissingQueryPourTexteBlank() throws Exception {
        controller.handleTextMessage(session,
            new TextMessage("{\"type\":\"query\",\"text\":\"   \"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        Map<?, ?> response = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
        assertThat(response.get("type")).isEqualTo("error");
        assertThat(((Map<?, ?>) response.get("error")).get("code")).isEqualTo("MISSING_QUERY");
    }

    // =========================================================================
    // US-27 / AC-27.8 — Message "cancel" → message "cancelled"
    // =========================================================================

    @Test
    @DisplayName("DOIT envoyer le message cancelled pour un message cancel")
    void devraitEnvoyerCancelledSurMessageCancel() throws Exception {
        controller.handleTextMessage(session, new TextMessage("{\"type\":\"cancel\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        Map<?, ?> msg = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
        assertThat(msg.get("type")).isEqualTo("cancelled");
        assertThat(msg.get("message")).isEqualTo("Stream annulé");
    }

    // =========================================================================
    // US-27 / AC-27.9 — Type inconnu → erreur UNKNOWN_TYPE
    // =========================================================================

    @Test
    @DisplayName("DOIT envoyer l'erreur UNKNOWN_TYPE pour un type de message inconnu")
    void devraitEnvoyerErreurUnknownTypePourTypeInconnu() throws Exception {
        controller.handleTextMessage(session, new TextMessage("{\"type\":\"weirdAction\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        Map<?, ?> response = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
        assertThat(response.get("type")).isEqualTo("error");
        assertThat(((Map<?, ?>) response.get("error")).get("code")).isEqualTo("UNKNOWN_TYPE");
    }

    // =========================================================================
    // US-27 / AC-27.10 — updateActivity appelé pour chaque message entrant
    // =========================================================================

    @Test
    @DisplayName("DOIT appeler updateActivity pour chaque message entrant")
    void devraitAppelerUpdateActivityPourChaqueMessage() throws Exception {
        controller.handleTextMessage(session, new TextMessage("{\"type\":\"cancel\"}"));

        verify(sessionManager).updateActivity("session-abc");
    }

    // =========================================================================
    // US-27 — conversationId null → chaîne vide dans query_received
    // =========================================================================

    @Test
    @DisplayName("DOIT utiliser une chaîne vide comme conversationId quand aucune conversation n'est active")
    void devraitUtiliserChaineVideSiAucuneConversationActive() throws Exception {
        when(sessionManager.getConversationId("session-abc")).thenReturn(null);

        controller.handleTextMessage(session,
            new TextMessage("{\"type\":\"query\",\"text\":\"Question sans conversation\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        Map<?, ?> msg = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
        assertThat(msg.get("conversationId")).isEqualTo("");
    }

    // =========================================================================
    // US-27 — broadcastToAll : message envoyé à toutes les sessions actives
    // =========================================================================

    @Test
    @DisplayName("DOIT diffuser le message à toutes les sessions actives via broadcastToAll")
    void devraitDiffuserMessageAToutesLesSessionsViaBroadcastToAll() throws Exception {
        // Enregistrement d'une session dans la map interne du handler
        controller.afterConnectionEstablished(session);
        clearInvocations(session); // efface les invocations de la connexion

        controller.broadcastToAll(Map.of("type", "notification", "message", "broadcast test"));

        verify(session).sendMessage(
            org.mockito.ArgumentMatchers.any(TextMessage.class));
    }
}
