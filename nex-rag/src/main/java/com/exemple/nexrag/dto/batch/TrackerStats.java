package com.exemple.nexrag.dto.batch;

/**
 * Statistiques instantanées du tracker.
 *
 * Principe SRP : unique responsabilité → porter les métriques du tracker.
 */
public record TrackerStats(
    int activeBatches,
    int textEmbeddings,
    int imageEmbeddings,
    int totalEmbeddings
) {}