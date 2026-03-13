package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Date;

/**
 * Réponse pour le health check détaillé.
 */
@Value
@Builder
public class DetailedHealthResponse {
    String  status;
    Boolean healthy;
    Integer strategies;
    Integer activeIngestions;
    Integer trackerBatches;
    Boolean redisHealthy;
    Boolean antivirusHealthy;
    Boolean antivirusEnabled;
    Date    timestamp;
}