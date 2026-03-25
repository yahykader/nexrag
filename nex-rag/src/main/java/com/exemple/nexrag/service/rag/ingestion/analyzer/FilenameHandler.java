package com.exemple.nexrag.service.rag.ingestion.analyzer;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utilitaire de sanitisation et de génération de noms de fichiers.
 *
 * Principe SRP : unique responsabilité → produire des noms de fichiers valides.
 * Clean code   : extrait {@code sanitizeFilename()} hors de {@link ImageSaver}.
 */
@Component
public class FilenameHandler {

    private static final int    MAX_LENGTH     = 200;
    private static final String FALLBACK_NAME  = "image";
    private static final DateTimeFormatter TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Nettoie un nom de fichier en retirant l'extension et les caractères invalides.
     */
    public String sanitize(String filename) {
        if (filename == null) return FALLBACK_NAME;

        // Retirer l'extension
        int dot = filename.lastIndexOf('.');
        if (dot > 0) filename = filename.substring(0, dot);

        String sanitized = filename
            .replaceAll("[\\\\/:*?\"<>|]", "_")
            .replaceAll("\\s+",            "_")
            .replaceAll("_{2,}",           "_")
            .trim();

        if (sanitized.length() > MAX_LENGTH) sanitized = sanitized.substring(0, MAX_LENGTH);
        return sanitized.isBlank() ? FALLBACK_NAME : sanitized;
    }

    /**
     * Génère un suffixe timestamp pour rendre un nom de fichier unique.
     */
    public String timestampSuffix() {
        return LocalDateTime.now().format(TIMESTAMP);
    }
}