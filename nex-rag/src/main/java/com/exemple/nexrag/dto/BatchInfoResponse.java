package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Réponse pour les informations d'un batch.
 */
@Value
@Builder
public class BatchInfoResponse {
    Boolean found;
    String  batchId;
    Integer textEmbeddings;
    Integer imageEmbeddings;
    Integer totalEmbeddings;
    String  message;
}