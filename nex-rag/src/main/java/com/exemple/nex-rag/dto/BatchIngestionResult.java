package com.exemple.nexrag.dto;

import java.util.List;

// ============================================================================
// Modèles de l'orchestrateur — extraits hors de IngestionOrchestrator
// Principe SRP : une classe par responsabilité, une classe par fichier Java
// ============================================================================

/**
 * Résultat agrégé d'un batch multi-fichiers.
 */
public record BatchIngestionResult(
    String                   batchId,
    List<FileIngestionResult> fileResults,
    int                      successCount,
    int                      failureCount,
    int                      totalTextEmbeddings,
    int                      totalImageEmbeddings,
    long                     durationMs,
    int                      duplicateCount
) {}