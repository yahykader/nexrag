package com.exemple.nexrag.service.rag.ingestion.analyzer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Propriétés de stockage des images extraites.
 *
 * Clean code : élimine le {@code @Value} dans {@link ImageSaver}.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "document.images")
public class ImageStorageProperties {

    /** Répertoire de stockage des images extraites. */
    @NotBlank
    private String storagePath = "./images";
}