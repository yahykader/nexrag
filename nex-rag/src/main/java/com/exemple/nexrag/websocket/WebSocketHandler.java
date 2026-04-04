package com.exemple.nexrag.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler WebSocket générique avec gestion de sessions
 * 
 * Features:
 * - Session management
 * - Message routing par type
 * - Heartbeat/ping-pong
 * - Error handling
 *
 * Principe SRP  : unique responsabilité → gérer le cycle de vie des
 *                 connexions WebSocket et router les messages par type.
 * Clean code    : {@code sessionData} typé comme {@code Map<String, Map<String, Object>>}
 *                 au lieu de {@code Map<String, Object>} — évite les casts non vérifiés.
 *                 {@code broadcast()} supprimé de la sous-classe — défini une seule fois ici.
 *
 * @author RAG Team
 * @version 2.0
 */
@Slf4j
public abstract class WebSocketHandler extends TextWebSocketHandler {

    protected final ObjectMapper                             objectMapper;
    protected final Map<String, WebSocketSession>            sessions    = new ConcurrentHashMap<>();
    protected final Map<String, Map<String, Object>>         sessionData = new ConcurrentHashMap<>();

    protected WebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Lifecycle — TextWebSocketHandler
    // -------------------------------------------------------------------------

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("🔌 WebSocket connecté : {} (total: {})", session.getId(), sessions.size());
        onConnectionEstablished(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) data.get("type");

            if (type == null) {
                sendError(session, "Le type de message est requis", "MISSING_TYPE");
                return;
            }

            switch (type) {
                case "ping" -> sendPong(session);
                case "pong" -> log.trace("💓 Pong reçu : {}", session.getId());
                default     -> handleMessage(session, type, data);
            }

        } catch (Exception e) {
            log.error("❌ Erreur traitement message WebSocket : {}", session.getId(), e);
            sendError(session, "Erreur traitement : " + e.getMessage(), "PROCESSING_ERROR");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        sessionData.remove(session.getId());
        log.info("🔌 WebSocket déconnecté : {} (status: {}, restants: {})",
            session.getId(), status, sessions.size());
        onConnectionClosed(session, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessions.remove(session.getId());
        sessionData.remove(session.getId());
        log.error("❌ Erreur transport WebSocket : {}", session.getId(), exception);
        onTransportError(session, exception);
    }

    // -------------------------------------------------------------------------
    // Méthodes à implémenter par les sous-classes
    // -------------------------------------------------------------------------

    protected abstract void onConnectionEstablished(WebSocketSession session);

    protected abstract void handleMessage(WebSocketSession session, String type,
                                          Map<String, Object> data);

    protected abstract void onConnectionClosed(WebSocketSession session, CloseStatus status);

    protected abstract void onTransportError(WebSocketSession session, Throwable exception);

    // -------------------------------------------------------------------------
    // Utilitaires — accessibles aux sous-classes
    // -------------------------------------------------------------------------

    protected void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            log.trace("📤 Message envoyé à {} : type={}", session.getId(), message.get("type"));
        } catch (IOException e) {
            log.error("❌ Échec envoi message WebSocket à {}", session.getId(), e);
        }
    }

    protected void broadcast(Map<String, Object> message) {
        sessions.values().forEach(s -> sendMessage(s, message));
    }

    protected void sendError(WebSocketSession session, String message, String code) {
        sendMessage(session, Map.of(
            "type",  "error",
            "error", Map.of(
                "message",   message,
                "code",      code,
                "timestamp", System.currentTimeMillis()
            )
        ));
    }

    protected void closeSession(String sessionId, CloseStatus status) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.close(status);
            } catch (IOException e) {
                log.error("❌ Erreur fermeture session {}", sessionId, e);
            }
        }
    }

    protected Map<String, Object> getSessionData(String sessionId) {
        return sessionData.getOrDefault(sessionId, Map.of());
    }

    protected void putSessionData(String sessionId, String key, Object value) {
        sessionData.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    protected int getActiveSessionCount() { return sessions.size(); }

    protected String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private void sendPong(WebSocketSession session) {
        log.trace("💓 Ping reçu → pong : {}", session.getId());
        sendMessage(session, Map.of("type", "pong", "timestamp", System.currentTimeMillis()));
    }
}