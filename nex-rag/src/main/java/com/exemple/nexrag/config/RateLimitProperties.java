package com.exemple.nexrag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriétés de configuration du Rate Limiting.
 *
 * Principe DIP  : centralise les quotas sous le préfixe {@code rate-limit.*}.
 * Clean code    : Redis supprimé — déjà configuré sous {@code spring.redis.*},
 *                 pas besoin de le dupliquer ici.
 *                 Le champ {@code defaultEndpoint} mappe sur la clé YAML
 *                 {@code default-endpoint} car {@code default} est un mot
 *                 réservé Java.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private Endpoint upload          = new Endpoint(10);
    private Endpoint batch           = new Endpoint(5);
    private Endpoint delete          = new Endpoint(20);
    private Endpoint search          = new Endpoint(50);
    private Endpoint defaultEndpoint = new Endpoint(30); // YAML: default-endpoint

    @Getter
    @Setter
    public static class Endpoint {
        private int requestsPerMinute;

        public Endpoint(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }
}