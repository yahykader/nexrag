package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Réponse pour un upload synchrone.
 */
@Value
@Builder
public class IngestionResponse {
    Boolean success;
    String  batchId;
    String  filename;
    Long    fileSize;
    Integer textEmbeddings;
    Integer imageEmbeddings;
    Long    durationMs;
    Boolean streamingUsed;
    String  message;
    Boolean duplicate;
    String  existingBatchId;
}