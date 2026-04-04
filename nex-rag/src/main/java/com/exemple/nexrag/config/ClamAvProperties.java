package com.exemple.nexrag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Propriétés de configuration ClamAV.
 *
 * Principe SRP  : unique responsabilité → porter la configuration ClamAV.
 * Principe DIP  : les services dépendent de cette abstraction, pas de @Value éparpillés.
 * Clean code    : @Validated assure un fail-fast au démarrage si la config est invalide.
 *                 Tous les champs correspondent exactement aux clés YAML (kebab-case → camelCase).
 *
 * YAML attendu (prefix = "antivirus") :
 * <pre>
 * antivirus:
 *   enabled: true
 *   host: localhost
 *   port: 3310
 *   timeout: 45000
 *   chunk-size: 8192
 *   max-file-size: 104857600
 *   retry:
 *     attempts: 2
 *     delay-ms: 1000
 *   health-check:
 *     enabled: true
 *     interval: 120000
 * </pre>
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "antivirus")
public class ClamAvProperties {

    /** Active ou désactive le scan antivirus. */
    private boolean enabled = false;

    /** Hôte du daemon clamd. */
    @NotBlank
    private String host = "localhost";

    /** Port TCP du daemon clamd. */
    @Min(1) @Max(65535)
    private int port = 3310;

    /** Timeout en ms pour la connexion et la lecture socket. */
    @Positive
    private int timeout = 45_000;

    /** Taille des chunks INSTREAM envoyés à clamd (en bytes). */
    @Positive
    private int chunkSize = 8_192;

    /** Taille maximale d'un fichier scannable (en bytes). Défaut : 100 MB. */
    @Positive
    private long maxFileSize = 100L * 1024 * 1024;

    @Valid
    private Retry retry = new Retry();

    @Valid
    private HealthCheck healthCheck = new HealthCheck();

    // -------------------------------------------------------------------------
    // Nested : Retry
    // -------------------------------------------------------------------------

    @Getter
    @Setter
    public static class Retry {

        /** Nombre de tentatives en cas d'échec de connexion. */
        @Min(0)
        private int attempts = 2;

        /** Délai entre deux tentatives (en ms). */
        @Positive
        private long delayMs = 1_000;
    }

    // -------------------------------------------------------------------------
    // Nested : HealthCheck
    // -------------------------------------------------------------------------

    @Getter
    @Setter
    public static class HealthCheck {

        /** Active le health check périodique. */
        private boolean enabled = true;

        /** Intervalle entre deux vérifications (en ms). */
        @Positive
        private long interval = 120_000;
    }
}