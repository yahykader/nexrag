package com.exemple.nexrag.service.rag.ingestion.repository;

import com.exemple.nexrag.service.rag.ingestion.cache.RedisCacheCleanupService;
import com.exemple.nexrag.service.rag.ingestion.repository.EmbeddingQueryDao;
import com.exemple.nexrag.service.rag.ingestion.repository.EmbeddingStoreDeleter;
import com.exemple.nexrag.service.rag.ingestion.tracker.IngestionTracker;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository principal d'embeddings.
 *
 * Principe SRP : unique responsabilité → orchestrer les opérations CRUD
 *                en déléguant à des composants spécialisés.
 * Principe DIP : dépend des abstractions EmbeddingStore, EmbeddingQueryDao,
 *                EmbeddingStoreDeleter, RedisCacheCleanupService.
 * Clean code   : 4 dépendances au lieu de 8, zéro duplication text/image,
 *                zéro SQL inline, zéro logique Redis.
 *
 * @author RAG Team
 * @version 2.0
 */
@Slf4j
@Repository
public class EmbeddingRepository {

    private static final String LABEL_TEXT  = "TEXT";
    private static final String LABEL_IMAGE = "IMAGE";

    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingStore<TextSegment> imageStore;
    private final EmbeddingStoreDeleter       storeDeleter;
    private final EmbeddingQueryDao           queryDao;
    private final RedisCacheCleanupService    cacheCleanup;
    private final IngestionTracker            tracker;

    @Value("${pgvector.text.table:text_embeddings}")
    private String textTableName;

    @Value("${pgvector.image.table:image_embeddings}")
    private String imageTableName;

    public EmbeddingRepository(
            @Qualifier("textEmbeddingStore")  EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            EmbeddingStoreDeleter    storeDeleter,
            EmbeddingQueryDao        queryDao,
            RedisCacheCleanupService cacheCleanup,
            IngestionTracker         tracker) {

        this.textStore    = textStore;
        this.imageStore   = imageStore;
        this.storeDeleter = storeDeleter;
        this.queryDao     = queryDao;
        this.cacheCleanup = cacheCleanup;
        this.tracker      = tracker;

        log.info("✅ EmbeddingRepository initialisé");
    }

    // =========================================================================
    // SUPPRESSION INDIVIDUELLE
    // =========================================================================

    public boolean deleteText(String embeddingId) {
        return storeDeleter.deleteById(textStore, embeddingId, LABEL_TEXT);
    }

    public boolean deleteImage(String embeddingId) {
        return storeDeleter.deleteById(imageStore, embeddingId, LABEL_IMAGE);
    }

    // =========================================================================
    // SUPPRESSION PAR LISTE D'IDS
    // =========================================================================

    public int deleteTextBatch(List<String> ids) {
        return storeDeleter.deleteByIds(textStore, ids, LABEL_TEXT);
    }

    public int deleteImageBatch(List<String> ids) {
        return storeDeleter.deleteByIds(imageStore, ids, LABEL_IMAGE);
    }

    // =========================================================================
    // SUPPRESSION PAR BATCH ID
    // =========================================================================

    /**
     * Supprime tous les embeddings (texte + images) d'un batch
     * et nettoie les caches Redis associés.
     */
    public int deleteBatch(String batchId) {
        log.info("🗑️ Suppression batch : {}", batchId);

        int textDeleted  = storeDeleter.deleteByBatchId(textStore,  textTableName,  batchId, LABEL_TEXT);
        int imageDeleted = storeDeleter.deleteByBatchId(imageStore, imageTableName, batchId, LABEL_IMAGE);
        int total        = textDeleted + imageDeleted;

        tracker.removeBatch(batchId);
        cacheCleanup.cleanupBatch(batchId);

        log.info("✅ Batch supprimé : {} — {} embeddings (text={}, image={})",
            batchId, total, textDeleted, imageDeleted);

        return total;
    }

    // =========================================================================
    // SUPPRESSION GLOBALE
    // =========================================================================

    /**
     * Supprime TOUS les embeddings (texte + images) + caches Redis + tracker.
     */
    public int deleteAllFilesPlusCache() {
        log.warn("🚨 SUPPRESSION GLOBALE DEMANDÉE");

        int textDeleted  = storeDeleter.deleteAll(textStore,  textTableName,  LABEL_TEXT);
        int imageDeleted = storeDeleter.deleteAll(imageStore, imageTableName, LABEL_IMAGE);
        int total        = textDeleted + imageDeleted;

        cacheCleanup.cleanupAll();
        tracker.clearAll();

        log.warn("✅ SUPPRESSION GLOBALE TERMINÉE — {} embeddings supprimés", total);
        return total;
    }

    /**
     * Nettoie le tracking mémoire et tous les caches Redis.
     */
    public void clearAllTracking() {
        log.warn("🗑️ Nettoyage complet du tracker et des caches");
        tracker.clearAll();
        cacheCleanup.cleanupAll();
        log.info("✅ Nettoyage complet terminé");
    }

    // =========================================================================
    // VÉRIFICATION / STATISTIQUES
    // =========================================================================

    public boolean batchExists(String batchId) {
        if (tracker.batchExists(batchId)) return true;

        Map<String, Integer> stats = getBatchStats(batchId);
        return stats.get("textEmbeddings") > 0 || stats.get("imageEmbeddings") > 0;
    }

    public Map<String, Integer> getBatchStats(String batchId) {
        int textCount  = queryDao.countByBatchId(textTableName,  batchId);
        int imageCount = queryDao.countByBatchId(imageTableName, batchId);

        log.debug("📊 Stats batch {} — text={}, image={}", batchId, textCount, imageCount);

        Map<String, Integer> stats = new HashMap<>();
        stats.put("textEmbeddings",  textCount);
        stats.put("imageEmbeddings", imageCount);
        return stats;
    }

    public int countTextByBatchId(String batchId) {
        return queryDao.countByBatchId(textTableName, batchId);
    }

    public int countImageByBatchId(String batchId) {
        return queryDao.countByBatchId(imageTableName, batchId);
    }

    public int countAllText() {
        return queryDao.countAll(textTableName);
    }

    public int countAllImage() {
        return queryDao.countAll(imageTableName);
    }
}