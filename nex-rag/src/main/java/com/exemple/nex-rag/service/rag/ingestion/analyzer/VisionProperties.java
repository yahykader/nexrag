package com.exemple.nexrag.service.rag.ingestion.analyzer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Propriétés de configuration du service Vision AI.
 *
 * Principe DIP  : les services dépendent de cette abstraction, pas de @Value éparpillés.
 * Clean code    : centralise les 4 @Value de VisionAnalyzer.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "vision")
public class VisionProperties {

    @Positive
    private int maxTokens = 500;

    @NotBlank
    private String imageQuality = "auto";

    @Positive
    private int timeoutSeconds = 30;

    @NotBlank
    private String prompt =
        "Analysez cette image et fournissez une description détaillée de son contenu, " +
        "y compris tout texte, objet, personne et contexte de la scène.";
}