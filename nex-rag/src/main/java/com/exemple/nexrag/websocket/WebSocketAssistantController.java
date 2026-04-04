package com.exemple.nexrag.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;

/**
 * Handler WebSocket pour le RAG Assistant — streaming et gestion de session.
 *
 * Principe SRP  : unique responsabilité → gérer les messages RAG assistant.
 *                 La gestion des sessions est déléguée à {@link WebSocketSessionManager}.
 * Principe DIP  : dépend des abstractions {@link WebSocketSessionManager} injectée
 *                 par constructeur — pas de {@code @Autowired} field injection.
 * Clean code    : supprime {@code simulateResponse()} avec {@code new Thread()} —
 *                 le mock de réponse n'appartient pas au code de production.
 *                 {@code broadcast()} défini dans {@link WebSocketHandler} — pas dupliqué.
 *
 * @author ayhyaoui
 * @version 2.0
 */
@Slf4j
@Component
public class WebSocketAssistantController extends WebSocketHandler {

    private static final String KEY_CONVERSATION_ID = "conversationId";

    private final WebSocketSessionManager sessionManager;

    public WebSocketAssistantController(ObjectMapper objectMapper,
                                        WebSocketSessionManager sessionManager) {
        super(objectMapper);
        this.sessionManager = sessionManager;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onConnectionEstablished(WebSocketSession session) {
        sessionManager.registerSession(session, "anonymous");
        log.info("✅ RAG Assistant connecté : {}", session.getId());

        sendMessage(session, Map.of(
            "type",      "connected",
            "sessionId", session.getId(),
            "timestamp", System.currentTimeMillis()
        ));
    }

    @Override
    protected void onConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.unregisterSession(session.getId());
        log.info("✅ RAG Assistant déconnecté : {} (status: {})", session.getId(), status);
    }

    @Override
    protected void onTransportError(WebSocketSession session, Throwable exception) {
        log.error("❌ Erreur transport RAG Assistant : {}", session.getId(), exception);
        sessionManager.unregisterSession(session.getId());
    }

    // -------------------------------------------------------------------------
    // Routage des messages
    // -------------------------------------------------------------------------

    @Override
    protected void handleMessage(WebSocketSession session, String type,
                                 Map<String, Object> data) {
        sessionManager.updateActivity(session.getId());
        log.info("📨 Message type={} depuis {}", type, session.getId());

        switch (type) {
            case "init"  -> handleInit(session, data);
            case "query" -> handleQuery(session, data);
            case "cancel"-> handleCancel(session);
            default      -> sendError(session, "Type inconnu : " + type, "UNKNOWN_TYPE");
        }
    }

    // -------------------------------------------------------------------------
    // Handlers métier
    // -------------------------------------------------------------------------

    private void handleInit(WebSocketSession session, Map<String, Object> data) {
        String userId         = (String) data.getOrDefault("userId", "anonymous");
        String conversationId = "conv_" + UUID.randomUUID().toString().substring(0, 8);

        sessionManager.setConversationId(session.getId(), conversationId);
        log.info("🔧 Init : user={} conv={}", userId, conversationId);

        sendMessage(session, Map.of(
            "type",           "conversation_created",
            "conversationId", conversationId,
            "userId",         userId
        ));
    }

    private void handleQuery(WebSocketSession session, Map<String, Object> data) {
        String query = (String) data.get("text");

        if (query == null || query.isBlank()) {
            sendError(session, "Le texte de la requête est requis", "MISSING_QUERY");
            return;
        }

        String conversationId = sessionManager.getConversationId(session.getId());
        log.info("🚀 Query : {} (conv: {})", truncate(query, 50), conversationId);

        sendMessage(session, Map.of(
            "type",           "query_received",
            "query",          query,
            "conversationId", conversationId != null ? conversationId : ""
        ));

        // TODO: déléguer à StreamingOrchestrator quand disponible
    }

    private void handleCancel(WebSocketSession session) {
        log.info("🛑 Annulation demandée par {}", session.getId());
        sendMessage(session, Map.of("type", "cancelled", "message", "Stream annulé"));
    }

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    /**
     * Diffuse un message à toutes les sessions actives.
     * Délègue à {@link WebSocketHandler#broadcast(Map)} — pas de duplication.
     */
    public void broadcastToAll(Map<String, Object> message) {
        broadcast(message);
        log.info("📢 Broadcast envoyé à {} sessions", getActiveSessionCount());
    }
}