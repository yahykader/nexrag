package com.exemple.nexrag.websocket;

import com.exemple.nexrag.config.WebSocketProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec unitaire de {@link WebSocketCleanupTask}.
 *
 * Principe SRP  : valide uniquement la délégation aux dépendances —
 *                 pas de vérification du mécanisme de scheduling Spring.
 * Principe DIP  : {@link WebSocketSessionManager} et {@link WebSocketProperties}
 *                 injectés comme mocks — pas d'AppContext Spring démarré.
 * Clean code    : les méthodes @Scheduled sont appelées directement ;
 *                 le scheduling Spring n'est pas testé (infrastructure).
 *
 * @see WebSocketCleanupTask
 */
@DisplayName("Spec : WebSocketCleanupTask — Nettoyage planifié des sessions WebSocket inactives")
@ExtendWith(MockitoExtension.class)
class WebSocketCleanupTaskSpec {

    @Mock
    private WebSocketSessionManager sessionManager;

    @Mock
    private WebSocketProperties props;

    @InjectMocks
    private WebSocketCleanupTask task;

    // =========================================================================
    // US-28 / AC-28.1 — cleanupInactiveSessions délègue avec le bon seuil
    // =========================================================================

    @Test
    @DisplayName("DOIT déléguer au sessionManager avec le seuil inactiveThresholdMs configuré")
    void devraitDelegugerAuSessionManagerAvecLeSeuilConfigure() {
        when(props.getInactiveThresholdMs()).thenReturn(3_600_000L);
        when(sessionManager.getActiveSessionCount()).thenReturn(5, 3, 3);

        task.cleanupInactiveSessions();

        verify(sessionManager).cleanupInactiveSessions(3_600_000L);
    }

    // =========================================================================
    // US-28 / AC-28.2 — logStats appelle getStats une seule fois
    // =========================================================================

    @Test
    @DisplayName("DOIT appeler getStats sur le sessionManager lors du log des statistiques")
    void devraitAppelerGetStatsDansLogStats() {
        WebSocketSessionManager.SessionStats stats =
            new WebSocketSessionManager.SessionStats(5, 3, 10L, 2.0, 500L);
        when(sessionManager.getStats()).thenReturn(stats);

        task.logStats();

        verify(sessionManager, times(1)).getStats();
    }

    // =========================================================================
    // US-28 / AC-28.3 — getActiveSessionCount : appelé avant et après nettoyage
    // =========================================================================

    @Test
    @DisplayName("DOIT appeler getActiveSessionCount au moins deux fois (avant et après nettoyage)")
    void devraitAppelerGetActiveSessionCountAvantEtApresNettoyage() {
        when(props.getInactiveThresholdMs()).thenReturn(1_000L);
        // 1er appel = avant (5), 2e = après (3), 3e = dans le log (3)
        when(sessionManager.getActiveSessionCount()).thenReturn(5, 3, 3);

        task.cleanupInactiveSessions();

        verify(sessionManager, atLeast(2)).getActiveSessionCount();
    }

    // =========================================================================
    // US-28 / AC-28.4 — Le seuil provient bien de WebSocketProperties
    // =========================================================================

    @Test
    @DisplayName("DOIT lire le seuil d'inactivité depuis WebSocketProperties")
    void devraitLireLeSeuilDepuisWebSocketProperties() {
        when(props.getInactiveThresholdMs()).thenReturn(7_200_000L);
        when(sessionManager.getActiveSessionCount()).thenReturn(0, 0, 0);

        task.cleanupInactiveSessions();

        verify(props).getInactiveThresholdMs();
        verify(sessionManager).cleanupInactiveSessions(7_200_000L);
    }
}
