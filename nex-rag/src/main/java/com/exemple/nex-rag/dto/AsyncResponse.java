package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Réponse pour un upload asynchrone.
 */
@Value
@Builder
public class AsyncResponse {
    Boolean accepted;
    String  batchId;
    String  filename;
    String  message;
    String  statusUrl;
    Boolean duplicate;
    String  existingBatchId;
}