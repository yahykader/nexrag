package com.exemple.nexrag.dto.batch;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Informations d'un batch pour les opérations CRUD.
 *
 * Principe SRP : unique responsabilité → porter les métadonnées d'un batch.
 */
public record BatchInfo(
    String        batchId,
    String        filename,
    String        mimeType,
    LocalDateTime timestamp,
    List<String>  textEmbeddings,
    List<String>  imageEmbeddings
) {
    public int totalEmbeddings() {
        return textEmbeddings.size() + imageEmbeddings.size();
    }
}