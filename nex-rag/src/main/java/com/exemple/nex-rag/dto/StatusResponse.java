package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Réponse pour le statut d'un batch.
 */
@Value
@Builder
public class StatusResponse {
    Boolean found;
    String  batchId;
    Integer textEmbeddings;
    Integer imageEmbeddings;
    Integer totalEmbeddings;
    String  message;
}