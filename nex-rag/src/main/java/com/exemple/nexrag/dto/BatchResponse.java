package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Réponse pour un upload batch simple.
 */
@Value
@Builder
public class BatchResponse {
    Boolean         success;
    String          batchId;
    Integer         fileCount;
    List<String>    filenames;
    Long            totalSize;
    String          message;
    String          statusUrl;
    DuplicateSummary duplicates;
}