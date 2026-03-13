package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Date;

/**
 * Détail d'une ingestion active.
 * Remplace {@code Map<String, Object>} non typé.
 */
@Value
@Builder
public class IngestionStatusInfo {
    String  batchId;
    String  filename;
    String  strategy;
    Date    startTime;
    boolean completed;
    boolean success;
    long    duration;
}