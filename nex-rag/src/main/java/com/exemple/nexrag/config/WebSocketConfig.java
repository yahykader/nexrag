package com.exemple.nexrag.config;

import com.exemple.nexrag.websocket.WebSocketAssistantController;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * Configuration WebSocket — STOMP + Raw WebSocket.
 *
 * Support 2 modes:
 * 1. STOMP over WebSocket (/ws) - pour upload progress tracking
 * 2. Raw WebSocket (/ws/assistant) - pour RAG streaming
 *
 * Supporte:
 * - WebSocket natif (recommandé)
 * - SockJS fallback (navigateurs anciens)
 * - CORS configuré via {@link WebSocketProperties}
 *
 * Principe SRP  : configure uniquement l'infrastructure WebSocket.
 *                 La logique métier reste dans les handlers.
 * Principe DIP  : dépend de {@link WebSocketProperties} pour les origines
 *                 et timeouts — pas de magic strings inline.
 * Clean code    : origines extraites dans {@link WebSocketProperties},
 *                 supprime la duplication entre les deux endpoints.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

    private final WebSocketAssistantController assistantHandler;
    private final WebSocketProperties          props;

    // -------------------------------------------------------------------------
    // STOMP — upload progress tracking
    // -------------------------------------------------------------------------

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
        log.info("✅ Message broker configuré : /topic, /queue, /app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = originsArray();

        // Endpoint natif
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(origins);

        // Endpoint SockJS fallback
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS()
            .setSessionCookieNeeded(false)
            .setClientLibraryUrl(
                "https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"
            );

        log.info("✅ STOMP endpoints enregistrés : /ws (natif + SockJS)");
    }

    // -------------------------------------------------------------------------
    // Raw WebSocket — RAG assistant streaming
    // -------------------------------------------------------------------------

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = originsArray();

        // Handler natif
        registry.addHandler(assistantHandler, "/ws/assistant")
            .setAllowedOrigins(origins);

        // Handler SockJS fallback
        registry.addHandler(assistantHandler, "/ws/assistant")
            .setAllowedOrigins("*")
            .withSockJS()
            .setSessionCookieNeeded(false);

        log.info("✅ Raw WebSocket handler enregistré : /ws/assistant (natif + SockJS)");
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private String[] originsArray() {
        return props.getAllowedOrigins().toArray(String[]::new);
    }
}