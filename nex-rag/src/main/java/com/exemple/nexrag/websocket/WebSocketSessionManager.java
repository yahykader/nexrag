package com.exemple.nexrag.websocket;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire de sessions WebSocket.
 *
 * Principe SRP  : unique responsabilité → gérer le cycle de vie et les
 *                 métadonnées des sessions WebSocket.
 * Clean code    : {@link SessionInfo} remplace {@code @Data} par {@code @Getter}
 *                 uniquement — les setters générés par {@code @Data} sont inutiles
 *                 sur une classe dont les champs sont mutés directement.
 *                 {@link SessionStats} est immuable — construit via stream,
 *                 sans le pattern {@code final long[] {0}} contournement.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final Map<String, SessionInfo>       sessions          = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession>  activeConnections = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // API publique — cycle de vie
    // -------------------------------------------------------------------------

    public void registerSession(WebSocketSession session, String userId) {
        String sessionId = session.getId();
        sessions.put(sessionId, new SessionInfo(sessionId, userId));
        activeConnections.put(sessionId, session);
        log.info("📝 Session enregistrée : {} (user: {}, total: {})",
            sessionId, userId, sessions.size());
    }

    public void unregisterSession(String sessionId) {
        SessionInfo info = sessions.remove(sessionId);
        activeConnections.remove(sessionId);
        if (info != null) {
            info.markDisconnected();
            log.info("📝 Session supprimée : {} (durée: {}ms, total: {})",
                sessionId, info.durationMs(), sessions.size());
        }
    }

    public void updateActivity(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        if (info != null) info.touch();
    }

    // -------------------------------------------------------------------------
    // API publique — lecture
    // -------------------------------------------------------------------------

    public SessionInfo       getSessionInfo(String sessionId)  { return sessions.get(sessionId);          }
    public WebSocketSession  getSession(String sessionId)      { return activeConnections.get(sessionId); }
    public int               getActiveSessionCount()           { return activeConnections.size();         }
    public boolean           isActive(String sessionId)        { return activeConnections.containsKey(sessionId); }

    public Set<String> getActiveSessionIds() {
        return new HashSet<>(activeConnections.keySet());
    }

    public void setConversationId(String sessionId, String conversationId) {
        SessionInfo info = sessions.get(sessionId);
        if (info != null) info.conversationId = conversationId;
    }

    public String getConversationId(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        return info != null ? info.conversationId : null;
    }

    // -------------------------------------------------------------------------
    // Statistiques
    // -------------------------------------------------------------------------

    public SessionStats getStats() {
        int    total         = sessions.size();
        int    active        = activeConnections.size();
        long   totalMessages = sessions.values().stream().mapToLong(s -> s.messageCount).sum();
        long   totalDuration = sessions.values().stream().mapToLong(SessionInfo::durationMs).sum();
        double avgMessages   = total > 0 ? (double) totalMessages / total : 0.0;
        long   avgDuration   = total > 0 ? totalDuration / total : 0L;

        return new SessionStats(total, active, totalMessages, avgMessages, avgDuration);
    }

    // -------------------------------------------------------------------------
    // Nettoyage
    // -------------------------------------------------------------------------

    public void cleanupInactiveSessions(long inactiveThresholdMs) {
        long now     = System.currentTimeMillis();
        long before  = sessions.size();

        sessions.entrySet().removeIf(entry -> {
            SessionInfo info = entry.getValue();
            return !info.active
                && info.disconnectionTime > 0
                && (now - info.disconnectionTime) > inactiveThresholdMs;
        });

        long cleaned = before - sessions.size();
        if (cleaned > 0) log.info("🧹 {} sessions inactives supprimées", cleaned);
    }

    // -------------------------------------------------------------------------
    // Modèles internes
    // -------------------------------------------------------------------------

    /**
     * Métadonnées d'une session WebSocket.
     *
     * Clean code : {@code @Getter} uniquement — pas de setters générés
     *              inutilement par {@code @Data}.
     */
    @Getter
    public static class SessionInfo {
        private final String sessionId;
        private final String userId;
        private final long   connectionTime;
        String  conversationId;
        long    lastActivity;
        long    disconnectionTime;
        boolean active        = true;
        int     messageCount  = 0;

        public SessionInfo(String sessionId, String userId) {
            this.sessionId      = sessionId;
            this.userId         = userId;
            this.connectionTime = System.currentTimeMillis();
            this.lastActivity   = this.connectionTime;
        }

        void touch() {
            lastActivity = System.currentTimeMillis();
            messageCount++;
        }

        void markDisconnected() {
            active           = false;
            disconnectionTime = System.currentTimeMillis();
        }

        long durationMs() {
            long end = active ? System.currentTimeMillis() : disconnectionTime;
            return end - connectionTime;
        }
    }

    /**
     * Statistiques immuables des sessions WebSocket.
     *
     * Clean code : record-like — construit une seule fois via {@link #getStats()},
     *              pas de setters. Remplace le pattern {@code @Data} mutable.
     */
    @Getter
    public static class SessionStats {
        private final int    totalSessions;
        private final int    activeSessions;
        private final long   totalMessages;
        private final double avgMessagesPerSession;
        private final long   avgConnectionDuration;

        public SessionStats(int total, int active, long totalMessages,
                     double avgMessages, long avgDuration) {
            this.totalSessions         = total;
            this.activeSessions        = active;
            this.totalMessages         = totalMessages;
            this.avgMessagesPerSession = avgMessages;
            this.avgConnectionDuration = avgDuration;
        }
    }
}