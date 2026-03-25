package com.exemple.nexrag.dto;

import com.exemple.nexrag.dto.IngestionResult;

/**
 * Résultat de l'ingestion d'un fichier dans un batch.
 *
 * Clean code : extrait hors de {@code IngestionOrchestrator} — une classe
 *              par fichier, une responsabilité par classe.
 */
public record FileIngestionResult(
    String          filename,
    boolean         success,
    String          errorMessage,
    IngestionResult result,
    boolean         duplicate
) {
    public static FileIngestionResult ok(String filename, IngestionResult result) {
        return new FileIngestionResult(filename, true, null, result, false);
    }

    public static FileIngestionResult duplicate(String filename, String existingBatchId) {
        String msg = "DUPLICATE - Already processed" +
            (existingBatchId != null ? " (batch: " + existingBatchId + ")" : "");
        return new FileIngestionResult(filename, false, msg, null, true);
    }

    public static FileIngestionResult error(String filename, String message) {
        return new FileIngestionResult(filename, false, message, null, false);
    }
}