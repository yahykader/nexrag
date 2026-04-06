package com.exemple.nexrag.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Spec unitaire de {@link WebSocketSessionManager}.
 *
 * Principe SRP  : valide uniquement le cycle de vie et les statistiques des sessions.
 * Principe DIP  : {@link WebSocketSession} injecté comme mock —
 *                 aucune connexion réseau réelle.
 *
 * @see WebSocketSessionManager
 */
@DisplayName("Spec : WebSocketSessionManager — Cycle de vie et statistiques des sessions WebSocket")
@ExtendWith(MockitoExtension.class)
class WebSocketSessionManagerSpec {

    @Mock
    private WebSocketSession session;

    private WebSocketSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new WebSocketSessionManager();
        lenient().when(session.getId()).thenReturn("session-1");
    }

    // =========================================================================
    // US-24 — Cycle de vie des sessions / AC-24.1
    // =========================================================================

    @Test
    @DisplayName("DOIT enregistrer la session et la rendre active")
    void devraitEnregistrerSessionEtLaRendreActive() {
        manager.registerSession(session, "user-42");

        assertThat(manager.isActive("session-1")).isTrue();
        assertThat(manager.getActiveSessionCount()).isEqualTo(1);
    }

    // =========================================================================
    // US-24 — AC-24.2
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner les informations de session après enregistrement")
    void devraitRetournerInfosSessionApresEnregistrement() {
        manager.registerSession(session, "user-42");

        WebSocketSessionManager.SessionInfo info = manager.getSessionInfo("session-1");

        assertThat(info).isNotNull();
        assertThat(info.getSessionId()).isEqualTo("session-1");
        assertThat(info.getUserId()).isEqualTo("user-42");
        assertThat(info.isActive()).isTrue();
    }

    // =========================================================================
    // US-24 — AC-24.3
    // =========================================================================

    @Test
    @DisplayName("DOIT rendre la session inactive après désenregistrement")
    void devraitRendreSessionInactiveApresDesenregistrement() {
        manager.registerSession(session, "user-42");
        manager.unregisterSession("session-1");

        assertThat(manager.isActive("session-1")).isFalse();
        assertThat(manager.getActiveSessionCount()).isEqualTo(0);
    }

    // =========================================================================
    // US-24 — AC-24.4
    // =========================================================================

    @Test
    @DisplayName("DOIT incrémenter le compteur de messages à chaque updateActivity")
    void devraitIncrementerCompteurMessagesAChaquUpdateActivity() {
        manager.registerSession(session, "user-42");
        manager.updateActivity("session-1");
        manager.updateActivity("session-1");

        assertThat(manager.getSessionInfo("session-1").getMessageCount()).isEqualTo(2);
    }

    // =========================================================================
    // US-24 — AC-24.5 / AC-24.6
    // =========================================================================

    @Test
    @DisplayName("DOIT stocker et retrouver le conversationId de la session")
    void devraitStockerEtRetrouverlConversationId() {
        manager.registerSession(session, "user-42");
        manager.setConversationId("session-1", "conv-abc");

        assertThat(manager.getConversationId("session-1")).isEqualTo("conv-abc");
    }

    @Test
    @DisplayName("DOIT retourner null pour conversationId si la session est inconnue")
    void devraitRetournerNullPourConversationIdSessionInconnue() {
        assertThat(manager.getConversationId("unknown")).isNull();
    }

    // =========================================================================
    // US-24 — getSession
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner la WebSocketSession active via getSession")
    void devraitRetournerWebSocketSessionViaGetSession() {
        manager.registerSession(session, "user-42");

        assertThat(manager.getSession("session-1")).isSameAs(session);
    }

    // =========================================================================
    // US-25 — Statistiques / AC-25.1
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner des statistiques à zéro quand aucune session n'existe")
    void devraitRetournerStatsZeroSansAucuneSession() {
        WebSocketSessionManager.SessionStats stats = manager.getStats();

        assertThat(stats.getTotalSessions()).isZero();
        assertThat(stats.getActiveSessions()).isZero();
        assertThat(stats.getTotalMessages()).isZero();
        assertThat(stats.getAvgMessagesPerSession()).isZero();
        assertThat(stats.getAvgConnectionDuration()).isZero();
    }

    // =========================================================================
    // US-25 — AC-25.2
    // =========================================================================

    @Test
    @DisplayName("DOIT calculer les statistiques correctement avec une session active et des messages")
    void devraitCalculerStatsCorrectementAvecUneSessionActive() {
        manager.registerSession(session, "user-42");
        manager.updateActivity("session-1");
        manager.updateActivity("session-1");

        WebSocketSessionManager.SessionStats stats = manager.getStats();

        assertThat(stats.getTotalSessions()).isEqualTo(1);
        assertThat(stats.getActiveSessions()).isEqualTo(1);
        assertThat(stats.getTotalMessages()).isEqualTo(2L);
        assertThat(stats.getAvgMessagesPerSession()).isEqualTo(2.0);
    }

    // =========================================================================
    // US-25 — AC-25.3
    // =========================================================================

    @Test
    @DisplayName("DOIT inclure les identifiants actifs dans getActiveSessionIds")
    void devraitInclureIdentifiantsActifsDansGetActiveSessionIds() {
        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("session-2");

        manager.registerSession(session, "user-1");
        manager.registerSession(session2, "user-2");

        assertThat(manager.getActiveSessionIds())
            .containsExactlyInAnyOrder("session-1", "session-2");
    }

    // =========================================================================
    // US-25 — Nettoyage / AC-25.4
    // =========================================================================

    @Test
    @DisplayName("DOIT supprimer les sessions inactives dépassant le seuil de nettoyage")
    void devraitSupprimerSessionsInactivesDepassantLeSeuil() {
        manager.registerSession(session, "user-42");
        manager.unregisterSession("session-1"); // marque comme déconnectée

        // seuil -1ms : (now - disconnectionTime) > -1 est toujours vrai → suppression garantie
        manager.cleanupInactiveSessions(-1L);

        assertThat(manager.getSessionInfo("session-1")).isNull();
    }

    // =========================================================================
    // US-25 — AC-25.5
    // =========================================================================

    @Test
    @DisplayName("DOIT conserver les sessions actives lors d'un nettoyage avec seuil bas")
    void devraitConserverSessionsActivesLorsNettoyage() {
        manager.registerSession(session, "user-42");

        // Le nettoyage avec seuil 0ms ne doit pas toucher aux sessions encore actives
        manager.cleanupInactiveSessions(0L);

        assertThat(manager.getSessionInfo("session-1")).isNotNull();
        assertThat(manager.isActive("session-1")).isTrue();
    }
}
