package com.exemple.nexrag.service.rag.ingestion.strategy.commun;

import com.exemple.nexrag.service.rag.ingestion.deduplication.text.TextDeduplicationService;
import com.exemple.nexrag.dto.IngestionResult;
import com.exemple.nexrag.service.rag.ingestion.progress.ProgressNotifier;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Gestion du cycle de vie d'une ingestion : cleanup, métriques, erreurs.
 *
 * Principe SRP  : unique responsabilité → gérer les opérations transversales
 *                 communes à toutes les stratégies.
 * Clean code    : élimine le bloc {@code catch} dupliqué dans PDF, DOCX,
 *                 XLSX et TEXT (clearLocalCache + notifier.error + log + throw).
 *                 Élimine le bloc post-ingestion dupliqué
 *                 (clearLocalCache + stats + recordStrategyProcessing + notifier.completed).
 *
 * @author RAG Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionLifecycle {

    private final TextDeduplicationService textDeduplicationService;
    private final RAGMetrics               ragMetrics;

    // -------------------------------------------------------------------------
    // Post-traitement (succès)
    // -------------------------------------------------------------------------

    /**
     * À appeler en fin d'ingestion réussie.
     * Nettoie le cache local, log les stats de déduplication,
     * enregistre les métriques et notifie la completion.
     *
     * @param strategyName nom de la stratégie (pour les métriques)
     * @param batchId      identifiant du batch
     * @param filename     nom du fichier traité
     * @param result       résultat de l'ingestion
     * @param durationMs   durée totale en ms
     * @param notifier     notificateur de progression (peut être null)
     */
    public void onSuccess(
            String strategyName,
            String batchId,
            String filename,
            IngestionResult result,
            long durationMs,
            ProgressNotifier notifier) {

        cleanupAndLogStats(batchId);

        ragMetrics.recordStrategyProcessing(
            strategyName, durationMs, result.totalEmbeddings()
        );

        if (notifier != null) {
            notifier.completed(batchId, filename,
                result.textEmbeddings(), result.imageEmbeddings());
        }
    }

    // -------------------------------------------------------------------------
    // Gestion d'erreur
    // -------------------------------------------------------------------------

    /**
     * À appeler dans le bloc {@code catch} de la méthode {@code ingest()}.
     * Nettoie le cache local, notifie l'erreur et logue.
     *
     * @param strategyName nom de la stratégie (pour les logs)
     * @param batchId      identifiant du batch
     * @param filename     nom du fichier en erreur
     * @param e            exception levée
     * @param notifier     notificateur de progression (peut être null)
     */
    public void onError(
            String strategyName,
            String batchId,
            String filename,
            Exception e,
            ProgressNotifier notifier) {

        cleanupAndLogStats(batchId);

        if (notifier != null) {
            notifier.error(batchId, filename, e.getMessage());
        }

        log.error("❌ [{}] Erreur traitement : {}", strategyName, filename, e);
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private void cleanupAndLogStats(String batchId) {
        textDeduplicationService.clearLocalCache();

        var stats = textDeduplicationService.getStats(batchId);
        log.info("📊 [Dedup] Total indexés : {}, Cache local : {}",
            stats.totalIndexed(), stats.localCacheSize());
    }
}