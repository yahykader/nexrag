package com.exemple.nexrag.service.rag.ingestion.tracker;

import com.exemple.nexrag.service.rag.ingestion.tracker.BatchEmbeddings;
import com.exemple.nexrag.service.rag.ingestion.tracker.BatchEmbeddingRegistry;
import com.exemple.nexrag.service.rag.ingestion.tracker.BatchInfoRegistry;
import com.exemple.nexrag.service.rag.ingestion.tracker.RollbackExecutor;
import com.exemple.nexrag.dto.batch.BatchInfo;
import com.exemple.nexrag.dto.batch.TrackerStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service de tracking des ingestions — rollback transactionnel + info CRUD.
 *
 * Principe SRP  : unique responsabilité → coordonner le tracking.
 *                 Le rollback est dans {@link RollbackExecutor}.
 *                 Les IDs d'embeddings sont dans {@link BatchEmbeddingRegistry}.
 *                 Les métadonnées batch sont dans {@link BatchInfoRegistry}.
 * Clean code    : supprime les deux maps redondantes qui se synchronisaient
 *                 manuellement, supprime le {@code RedisTemplate} jamais utilisé,
 *                 supprime {@code getTotalEmbeddings}/{@code getTotalEmbeddingCount}
 *                 qui étaient identiques.
 *
 * @author RAG Team
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionTracker {

    private final BatchEmbeddingRegistry embeddingRegistry;
    private final BatchInfoRegistry      infoRegistry;
    private final RollbackExecutor       rollbackExecutor;

    // =========================================================================
    // Enregistrement
    // =========================================================================

    /**
     * Enregistre un nouveau batch avec ses métadonnées.
     */
    public void trackBatch(String batchId, String filename, String mimeType) {
        infoRegistry.register(batchId, filename, mimeType);
    }

    /**
     * Ajoute un embedding texte au tracking du batch.
     */
    public void addTextEmbeddingId(String batchId, String embeddingId) {
        if (isBlank(batchId) || isBlank(embeddingId)) return;
        embeddingRegistry.addTextEmbedding(batchId, embeddingId);
        infoRegistry.addTextEmbeddingId(batchId, embeddingId);
    }

    /**
     * Ajoute un embedding image au tracking du batch.
     */
    public void addImageEmbeddingId(String batchId, String embeddingId) {
        if (isBlank(batchId) || isBlank(embeddingId)) return;
        embeddingRegistry.addImageEmbedding(batchId, embeddingId);
        infoRegistry.addImageEmbeddingId(batchId, embeddingId);
    }

    // =========================================================================
    // Lecture
    // =========================================================================

    public List<String> getTextEmbeddingIds(String batchId) {
        return embeddingRegistry.getTextIds(batchId);
    }

    public List<String> getImageEmbeddingIds(String batchId) {
        return embeddingRegistry.getImageIds(batchId);
    }

    public Optional<BatchInfo> getBatchInfo(String batchId) {
        return infoRegistry.get(batchId);
    }

    public Map<String, BatchInfo> getAllBatches() {
        return infoRegistry.getAll();
    }

    public boolean batchExists(String batchId) {
        return infoRegistry.contains(batchId) || embeddingRegistry.contains(batchId);
    }

    // =========================================================================
    // Rollback
    // =========================================================================

    /**
     * Annule complètement une ingestion en supprimant tous ses embeddings.
     *
     * @return nombre d'embeddings supprimés
     * @throws RuntimeException si le rollback échoue
     */
    public int rollbackBatch(String batchId) {
        BatchEmbeddings embeddings = embeddingRegistry.get(batchId);

        if (embeddings == null) {
            log.warn("⚠️ [Tracker] Batch introuvable pour rollback : {}", batchId);
            return 0;
        }

        try {
            int deleted = rollbackExecutor.rollback(batchId, embeddings);
            removeBatch(batchId);
            return deleted;
        } catch (Exception e) {
            throw new IllegalStateException("Erreur rollback batch : " + batchId, e);
        }
    }

    // =========================================================================
    // Nettoyage
    // =========================================================================

    /**
     * Supprime un batch du tracker (après ingestion réussie ou rollback).
     */
    public void removeBatch(String batchId) {
        embeddingRegistry.remove(batchId);
        infoRegistry.remove(batchId);
        log.info("📊 [Tracker] Batch supprimé : {}", batchId);
    }

    /**
     * Libère la mémoire des IDs d'un batch après ingestion réussie.
     * Conserve les métadonnées dans {@link BatchInfoRegistry}.
     */
    public void clearBatch(String batchId) {
        BatchEmbeddings batch = embeddingRegistry.get(batchId);
        if (batch != null) {
            log.debug("✅ [Tracker] Batch nettoyé : {} — {}", batchId, batch);
        }
        embeddingRegistry.remove(batchId);
    }

    /**
     * Vide tous les trackers (suppression globale).
     */
    public void clearAll() {
        embeddingRegistry.clear();
        infoRegistry.clear();
        log.warn("⚠️ [Tracker] Tous les batches supprimés");
    }

    // =========================================================================
    // Statistiques
    // =========================================================================

    public TrackerStats getStats() {
        return embeddingRegistry.getStats();
    }

    public int getBatchCount() {
        return infoRegistry.size();
    }

    public int getTotalEmbeddings() {
        return infoRegistry.totalEmbeddings();
    }

    public void logStats() {
        TrackerStats s = getStats();
        log.info("📊 [Tracker] {} batch(es) actifs — {} embeddings (text={}, images={})",
            s.activeBatches(), s.totalEmbeddings(), s.textEmbeddings(), s.imageEmbeddings());
    }

    // =========================================================================
    // Privé
    // =========================================================================

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}