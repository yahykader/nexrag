package com.exemple.nexrag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Propriétés de configuration WebSocket.
 *
 * Principe DIP  : centralise les origines et timeouts au lieu de
 *                 les dupliquer dans {@link WebSocketConfig}.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "websocket")
public class WebSocketProperties {

    /**
     * Origines autorisées pour les connexions WebSocket.
     * Configurables sans recompilation.
     */
    private List<String> allowedOrigins = List.of(
        "http://localhost",
        "http://localhost:8080",
        "http://localhost:4200",
        "https://*"
    );

    /**
     * Durée d'inactivité avant qu'une session soit considérée inactive (ms).
     * Défaut : 1 heure.
     */
    private long inactiveThresholdMs = 3_600_000L;

    /**
     * Intervalle de nettoyage des sessions inactives (ms).
     * Défaut : 1 heure.
     */
    private long cleanupIntervalMs = 3_600_000L;

    /**
     * Intervalle de log des statistiques (ms).
     * Défaut : 5 minutes.
     */
    private long statsIntervalMs = 300_000L;
}