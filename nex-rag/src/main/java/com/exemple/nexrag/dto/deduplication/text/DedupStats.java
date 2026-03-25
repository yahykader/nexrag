package com.exemple.nexrag.dto.deduplication.text;

/**
 * Statistiques de déduplication de textes.
 *
 * Principe SRP : unique responsabilité → porter les données statistiques.
 */
public record DedupStats(
    boolean enabled,
    long    totalIndexed,
    long    localCacheSize,
    boolean batchIdScope
) {}