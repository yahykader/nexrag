package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Réponse pour les statistiques système.
 */
@Value
@Builder
public class SystemStatsResponse {
    Integer totalStrategies;
    Integer activeIngestions;
    Integer trackedBatches;
    Integer totalEmbeddings;
    Integer filesInProgress;
    Boolean redisHealthy;
    String  systemStatus;
}