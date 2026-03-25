package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Réponse pour un upload batch détaillé.
 */
@Value
@Builder
public class BatchDetailedResponse {
    Boolean          accepted;
    Boolean          success;
    String           batchId;
    Integer          fileCount;
    Long             totalSize;
    String           message;
    String           statusUrl;
    String           resultUrl;
    DuplicateSummary duplicates;
}