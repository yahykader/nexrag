package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Réponse pour les statistiques globales d'ingestion.
 */
@Value
@Builder
public class StatsResponse {
    Integer strategiesCount;
    Integer activeIngestions;
    Integer trackerBatches;
    Integer trackerEmbeddings;
    Integer filesInProgress;
}