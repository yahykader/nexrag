package com.exemple.nexrag.websocket;

import com.exemple.nexrag.config.WebSocketProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tâche planifiée de nettoyage des sessions WebSocket inactives.
 *
 * Principe SRP  : unique responsabilité → déclencher périodiquement le
 *                 nettoyage et le log des statistiques.
 * Principe DIP  : dépend de {@link WebSocketProperties} — les intervalles
 *                 sont configurables sans recompilation.
 * Clean code    : injection par constructeur via {@code @RequiredArgsConstructor}
 *                 au lieu de {@code @Autowired} field injection.
 *                 Magic numbers supprimés — intervalles dans {@link WebSocketProperties}.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketCleanupTask {

    private final WebSocketSessionManager sessionManager;
    private final WebSocketProperties     props;

    /**
     * Nettoyage des sessions inactives.
     * Intervalle configurable via {@code websocket.cleanup-interval-ms}.
     */
    @Scheduled(fixedRateString = "${websocket.cleanup-interval-ms:3600000}")
    public void cleanupInactiveSessions() {
        log.info("🧹 Démarrage nettoyage des sessions WebSocket inactives...");

        int before = sessionManager.getActiveSessionCount();
        sessionManager.cleanupInactiveSessions(props.getInactiveThresholdMs());
        int cleaned = before - sessionManager.getActiveSessionCount();

        log.info("🧹 Nettoyage terminé : {} supprimées, {} restantes",
            cleaned, sessionManager.getActiveSessionCount());
    }

    /**
     * Log des statistiques WebSocket.
     * Intervalle configurable via {@code websocket.stats-interval-ms}.
     */
    @Scheduled(fixedRateString = "${websocket.stats-interval-ms:300000}")
    public void logStats() {
        WebSocketSessionManager.SessionStats stats = sessionManager.getStats();
        log.info("📊 WebSocket — actives: {} | total: {} | moy. msg/session: {} | moy. durée: {}ms",
            stats.getActiveSessions(),
            stats.getTotalSessions(),
            "%.2f".formatted(stats.getAvgMessagesPerSession()),
            stats.getAvgConnectionDuration());
    }
}