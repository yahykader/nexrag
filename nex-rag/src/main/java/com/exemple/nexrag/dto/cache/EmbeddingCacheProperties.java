package com.exemple.nexrag.dto.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

/**
 * Propriétés de configuration du cache d'embeddings.
 *
 * Principe DIP  : les services dépendent de cette abstraction, pas de @Value éparpillés.
 * Clean code    : élimine le magic number {@code 24} hardcodé dans le constructeur.
 *
 * YAML attendu :
 * <pre>
 * embedding:
 *   cache:
 *     ttl-hours: 24
 *     enabled: true
 * </pre>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "embedding.cache")
public class EmbeddingCacheProperties {

    /** TTL des embeddings en cache (en heures). Défaut : 24h. */
    @Positive
    private int ttlHours = 24;

    @Positive
    private int maxSizeMb = 100;

    /** Active ou désactive le cache. */
    private boolean enabled = true;
}