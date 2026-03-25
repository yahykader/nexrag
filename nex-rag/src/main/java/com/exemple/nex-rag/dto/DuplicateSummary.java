package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Résumé des doublons détectés dans un batch.
 *
 * Clean code : remplace {@code Map<String, String>} non typé par un objet explicite.
 */
@Value
@Builder
public class DuplicateSummary {

    /** Nombre total de doublons détectés. */
    int count;

    /** Noms des fichiers en doublon. */
    List<String> filenames;

    /** Associe chaque nom de fichier à son batchId existant. */
    Map<String, String> existingBatchIds;

    public boolean hasNone() {
        return count == 0;
    }
}