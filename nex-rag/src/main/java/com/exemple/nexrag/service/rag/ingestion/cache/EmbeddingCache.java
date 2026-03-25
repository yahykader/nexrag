package com.exemple.nexrag.service.rag.ingestion.cache;

import com.exemple.nexrag.dto.cache.EmbeddingCacheProperties;
import com.exemple.nexrag.service.rag.ingestion.cache.*;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Cache Redis pour les embeddings avec métriques et tracking par batch.
 *
 * Principe SRP  : unique responsabilité → orchestrer les opérations de cache.
 *                 La sérialisation est dans {@link EmbeddingSerializer}.
 *                 Le hachage est dans {@link EmbeddingTextHasher}.
 *                 Les accès Redis sont dans {@link EmbeddingCacheStore}.
 * Principe DIP  : implémente {@link CacheCleanable} — s'intègre automatiquement
 *                 dans {@link RedisCacheCleanupService} via {@code List<CacheCleanable>}.
 * Clean code    : supprime {@code clear()} (alias de {@code clearAll()}),
 *                 supprime {@code truncate(maxLength)} (dupliqué dans toute l'app),
 *                 élimine le tracking batch dupliqué entre {@code put()} et {@code getAndTrack()}.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingCache implements CacheCleanable {

    private static final String CACHE_NAME     = "embedding";
    private static final int    LOG_MAX_LENGTH = 50;

    private final EmbeddingCacheProperties props;
    private final EmbeddingCacheStore      store;
    private final EmbeddingSerializer      serializer;
    private final EmbeddingTextHasher      textHasher;
    private final RAGMetrics               ragMetrics;

    // -------------------------------------------------------------------------
    // Lecture avec calcul à la demande
    // -------------------------------------------------------------------------

    /**
     * Retourne l'embedding depuis le cache ou le calcule via le {@code supplier}.
     */
    public Embedding getOrCompute(String text, Supplier<Embedding> supplier) {
        String textHash = textHasher.hash(text);
        String cached   = store.get(textHash);

        if (cached != null) {
            ragMetrics.recordCacheHit(CACHE_NAME);
            log.debug("✓ Cache HIT : {}", truncate(text));
            return serializer.deserialize(cached);
        }

        ragMetrics.recordCacheMiss(CACHE_NAME);
        log.debug("✗ Cache MISS : {}", truncate(text));

        Embedding embedding = supplier.get();
        store.save(textHash, serializer.serialize(embedding), props.getTtlHours());
        return embedding;
    }

    // -------------------------------------------------------------------------
    // Lecture avec tracking batch
    // -------------------------------------------------------------------------

    /**
     * Retourne l'embedding depuis le cache et l'associe au batch.
     * Retourne {@code null} si absent du cache.
     */
    public Embedding getAndTrack(String text, String batchId) {
        String textHash = textHasher.hash(text);
        String cached   = store.get(textHash);

        if (cached != null) {
            ragMetrics.recordCacheHit(CACHE_NAME);
            log.debug("✓ Cache HIT : {}", truncate(text));
            store.trackBatch(batchId, textHash, props.getTtlHours());
            return serializer.deserialize(cached);
        }

        ragMetrics.recordCacheMiss(CACHE_NAME);
        log.debug("✗ Cache MISS : {}", truncate(text));
        return null;
    }

    // -------------------------------------------------------------------------
    // Écriture
    // -------------------------------------------------------------------------

    /**
     * Met en cache un embedding et l'associe à un batch.
     */
    public void put(String text, Embedding embedding, String batchId) {
        String textHash = textHasher.hash(text);
        store.save(textHash, serializer.serialize(embedding), props.getTtlHours());
        store.trackBatch(batchId, textHash, props.getTtlHours());
        log.debug("✅ [Cache] Embedding mis en cache — batch : {}", batchId);
    }

    // -------------------------------------------------------------------------
    // Lecture simple
    // -------------------------------------------------------------------------

    public boolean exists(String text) {
        return store.exists(textHasher.hash(text));
    }

    public void evict(String text) {
        store.delete(textHasher.hash(text));
        log.debug("🗑️ Cache EVICT : {}", truncate(text));
    }

    // -------------------------------------------------------------------------
    // Statistiques
    // -------------------------------------------------------------------------

    public long countBatchEmbeddings(String batchId) {
        return store.countBatch(batchId);
    }

    // -------------------------------------------------------------------------
    // CacheCleanable — nettoyage
    // -------------------------------------------------------------------------

    @Override
    public void removeBatch(String batchId) {
        int deleted = store.deleteByBatchId(batchId);
        log.info("✅ [Cache] Batch supprimé : {} ({} clé(s))", batchId, deleted);
    }

    @Override
    public void clearAll() {
        int deleted = store.deleteAll();
        log.info("✅ [Cache] Tous les embeddings supprimés : {} clé(s)", deleted);
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private String truncate(String text) {
        return text != null && text.length() > LOG_MAX_LENGTH
            ? text.substring(0, LOG_MAX_LENGTH) + "..."
            : text;
    }
}