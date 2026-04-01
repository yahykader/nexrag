package com.exemple.nexrag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Propriétés de configuration du service Whisper.
 *
 * Principe DIP  : les services dépendent de cette abstraction,
 *                 pas de @Value éparpillés.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "openai.whisper")
public class WhisperProperties {

    /** Modèle Whisper à utiliser. */
    @NotBlank
    private String model = "whisper-1";

    /** Timeout de l'appel API en secondes. */
    @Positive
    private int timeoutSeconds = 30;

    /**
     * Extension par défaut si le fichier audio n'en a pas.
     * Whisper accepte nativement webm.
     */
    @NotBlank
    private String defaultExtension = ".webm";

    /** Taille minimale d'un audio valide en bytes. */
    @Positive
    private int minAudioBytes = 1_000;
}