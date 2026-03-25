package com.exemple.nexrag.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Propriétés de configuration OpenAI.
 *
 * Principe SRP : une seule responsabilité → portage des propriétés OpenAI.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    @NotBlank
    private String apiKey;

    private boolean enabled = true;

    private String embeddingModel = "text-embedding-3-small";

    private String chatModel = "gpt-4o";

    private double temperature = 0.7;

    @Positive
    private int maxTokens = 2000;

    @Positive
    private int timeoutSeconds = 60;

    @Positive
    private int maxRetries = 3;

    private boolean logRequests = false;

    private boolean logResponses = false;
}