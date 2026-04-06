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

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec unitaire de {@link WebSocketHandler}.
 *
 * Principe SRP  : valide le cycle de vie des connexions et le routage des
 *                 messages dans la classe abstraite de base.
 * Principe DIP  : {@link WebSocketSession} injecté comme mock — aucun
 *                 serveur WebSocket réel démarré.
 * Clean code    : {@link StubHandler} expose les méthodes protégées pour les
 *                 tests sans modifier le code de production.
 *
 * @see WebSocketHandler
 */
@DisplayName("Spec : WebSocketHandler — Cycle de vie des connexions et routage des messages")
@ExtendWith(MockitoExtension.class)
class WebSocketHandlerSpec {

    @Mock
    private WebSocketSession session;

    private ObjectMapper objectMapper;
    private StubHandler  handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler      = new StubHandler(objectMapper);
        lenient().when(session.getId()).thenReturn("session-test");
    }

    // =========================================================================
    // US-26 / AC-26.1 — Connexion établie : session enregistrée
    // =========================================================================

    @Test
    @DisplayName("DOIT enregistrer la session dans la map et appeler onConnectionEstablished")
    void devraitEnregistrerSessionEtAppelerOnConnectionEstablished() throws Exception {
        handler.afterConnectionEstablished(session);

        assertThat(handler.getActiveSessionCount()).isEqualTo(1);
        assertThat(handler.connectionEstablishedCalled).isTrue();
    }

    // =========================================================================
    // US-26 / AC-26.5 — Déconnexion : session retirée
    // =========================================================================

    @Test
    @DisplayName("DOIT retirer la session de la map et appeler onConnectionClosed")
    void devraitRetirerSessionEtAppelerOnConnectionClosed() throws Exception {
        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(handler.getActiveSessionCount()).isEqualTo(0);
        assertThat(handler.connectionClosedCalled).isTrue();
    }

    // =========================================================================
    // US-26 / AC-26.6 — Erreur transport : session retirée
    // =========================================================================

    @Test
    @DisplayName("DOIT retirer la session de la map et appeler onTransportError")
    void devraitRetirerSessionEtAppelerOnTransportError() throws Exception {
        handler.afterConnectionEstablished(session);
        handler.handleTransportError(session, new RuntimeException("Erreur réseau"));

        assertThat(handler.getActiveSessionCount()).isEqualTo(0);
        assertThat(handler.transportErrorCalled).isTrue();
    }

    // =========================================================================
    // US-26 / AC-26.2 — Message ping → réponse pong
    // =========================================================================

    @Test
    @DisplayName("DOIT répondre avec un message pong à un message ping")
    void devraitRepondrePongAUnMessagePing() throws Exception {
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"ping\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        Map<?, ?> response = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
        assertThat(response.get("type")).isEqualTo("pong");
        assertThat(response.get("timestamp")).isNotNull();
    }

    // =========================================================================
    // US-26 / AC-26.3 — Message sans type → erreur MISSING_TYPE
    // =========================================================================

    @Test
    @DisplayName("DOIT envoyer l'erreur MISSING_TYPE si le champ type est absent du message")
    void devraitEnvoyerErreurSiTypeAbsentDuMessage() throws Exception {
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        handler.handleTextMessage(session, new TextMessage("{\"data\":\"sans type\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        Map<?, ?> response = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
        assertThat(response.get("type")).isEqualTo("error");
        assertThat(((Map<?, ?>) response.get("error")).get("code")).isEqualTo("MISSING_TYPE");
    }

    // =========================================================================
    // US-26 / AC-26.4 — JSON invalide → erreur PROCESSING_ERROR
    // =========================================================================

    @Test
    @DisplayName("DOIT envoyer l'erreur PROCESSING_ERROR pour un payload JSON invalide")
    void devraitEnvoyerErreurProcessingPourJsonInvalide() throws Exception {
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        handler.handleTextMessage(session, new TextMessage("NOT_VALID_JSON"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        Map<?, ?> response = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
        assertThat(response.get("type")).isEqualTo("error");
        assertThat(((Map<?, ?>) response.get("error")).get("code")).isEqualTo("PROCESSING_ERROR");
    }

    // =========================================================================
    // US-26 / AC-26.7 — broadcast : message envoyé à toutes les sessions actives
    // =========================================================================

    @Test
    @DisplayName("DOIT envoyer le message broadcast à toutes les sessions actives")
    void devraitEnvoyerBroadcastAToutesLesSessions() throws Exception {
        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("session-2");

        handler.afterConnectionEstablished(session);
        handler.afterConnectionEstablished(session2);
        clearInvocations(session, session2);

        handler.invokeBroadcast(Map.of("type", "notification", "message", "hello"));

        verify(session).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    // =========================================================================
    // US-26 / AC-26.8 — Message pong → traité silencieusement (pas de réponse)
    // =========================================================================

    @Test
    @DisplayName("DOIT traiter silencieusement le message pong sans envoyer de réponse")
    void devraitIgnorerMessagePongSansEnvoyerReponse() throws Exception {
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        // Un message pong est simplement loggué, aucun sendMessage ne doit avoir lieu
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"pong\"}"));

        // session.sendMessage ne doit pas être appelé
        verify(session, org.mockito.Mockito.never()).sendMessage(any(TextMessage.class));
        assertThat(handler.lastMessageType).isNull(); // n'atteint pas handleMessage
    }

    // =========================================================================
    // US-26 / AC-26.9-10 — truncate
    // =========================================================================

    @Test
    @DisplayName("DOIT tronquer les chaînes plus longues que la limite avec '...'")
    void devraitTronquerLesChainersPlusLonguesQueLaLimite() {
        String result = handler.invokeTruncate("A".repeat(100), 50);

        assertThat(result).endsWith("...");
        assertThat(result.length()).isEqualTo(53);
    }

    @Test
    @DisplayName("DOIT retourner le texte intact s'il est inférieur ou égal à la limite")
    void devraitRetournerTexteIntactSiInferieurALaLimite() {
        assertThat(handler.invokeTruncate("Court", 50)).isEqualTo("Court");
    }

    @Test
    @DisplayName("DOIT retourner null si le texte est null")
    void devraitRetournerNullSiTexteNull() {
        assertThat(handler.invokeTruncate(null, 50)).isNull();
    }

    // =========================================================================
    // US-26 / AC-26.11 — putSessionData / getSessionData
    // =========================================================================

    @Test
    @DisplayName("DOIT stocker et récupérer des données de session via putSessionData/getSessionData")
    void devraitStockerEtRecupererDonneesDeSession() {
        handler.invokePutSessionData("session-test", "clé", "valeur");

        assertThat(handler.invokeGetSessionData("session-test")).containsEntry("clé", "valeur");
    }

    @Test
    @DisplayName("DOIT retourner une map vide pour une session sans données stockées")
    void devraitRetournerMapVidePourSessionSansDonnees() {
        assertThat(handler.invokeGetSessionData("session-inconnue")).isEmpty();
    }

    // =========================================================================
    // US-26 / FR-016 — broadcast : échec sur une session n'interrompt pas les autres
    // =========================================================================

    @Test
    @DisplayName("DOIT continuer le broadcast sur les sessions restantes après un échec d'envoi")
    void devraitContinuerBroadcastApresSendMessageEchoue() throws Exception {
        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("session-2");

        handler.afterConnectionEstablished(session);
        handler.afterConnectionEstablished(session2);
        clearInvocations(session, session2);

        // La première session lève IOException lors de l'envoi
        doThrow(new IOException("Erreur réseau simulée")).when(session).sendMessage(any(TextMessage.class));

        // Le broadcast ne doit pas s'interrompre : session2 doit recevoir le message
        handler.invokeBroadcast(Map.of("type", "test", "message", "broadcast-isolation"));

        verify(session2).sendMessage(any(TextMessage.class));
    }

    // =========================================================================
    // Sous-classe concrète minimale pour tester la classe abstraite
    // =========================================================================

    /**
     * Implémentation concrète minimale de {@link WebSocketHandler} exposant
     * les méthodes protégées et enregistrant les appels aux hooks du cycle de vie.
     */
    static class StubHandler extends WebSocketHandler {

        boolean connectionEstablishedCalled;
        boolean connectionClosedCalled;
        boolean transportErrorCalled;
        String  lastMessageType;

        StubHandler(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        protected void onConnectionEstablished(WebSocketSession session) {
            connectionEstablishedCalled = true;
        }

        @Override
        protected void handleMessage(WebSocketSession session, String type,
                                     Map<String, Object> data) {
            lastMessageType = type;
        }

        @Override
        protected void onConnectionClosed(WebSocketSession session, CloseStatus status) {
            connectionClosedCalled = true;
        }

        @Override
        protected void onTransportError(WebSocketSession session, Throwable exception) {
            transportErrorCalled = true;
        }

        // Exposition des méthodes protégées pour les tests
        void invokeBroadcast(Map<String, Object> message)               { broadcast(message); }
        String invokeTruncate(String text, int max)                     { return truncate(text, max); }
        Map<String, Object> invokeGetSessionData(String id)             { return getSessionData(id); }
        void invokePutSessionData(String id, String key, Object value)  { putSessionData(id, key, value); }
    }
}
