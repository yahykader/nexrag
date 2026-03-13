package com.exemple.nexrag.service.rag.ingestion.cache;

import com.exemple.nexrag.service.rag.ingestion.cache.EmbeddingCache;
import com.exemple.nexrag.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.nexrag.service.rag.ingestion.deduplication.TextDeduplicationService;
import com.exemple.nexrag.constant.EmbeddingRepositoryConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Service de nettoyage des caches Redis.
 *
 * Principe SRP : unique responsabilité → nettoyer les caches Redis.
 * Clean code   : élimine la duplication entre {@code cleanupRedisCaches()},
 *                {@code cleanupAllRedisCaches()} et {@code clearAllTracking()}
 *                qui faisaient les mêmes opérations en trois endroits différents.
 *
 * @author ayhyaoui
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheCleanupService {

    private final DeduplicationService      deduplicationService;
    private final TextDeduplicationService  textDeduplicationService;
    private final EmbeddingCache            embeddingCache;
    private final RedisTemplate<String, String> redisTemplate;

    // -------------------------------------------------------------------------
    // Nettoyage par batch
    // -------------------------------------------------------------------------

    /**
     * Nettoie tous les caches Redis pour un batch spécifique.
     */
    public void cleanupBatch(String batchId) {
        log.info("🧹 [Redis] Nettoyage des caches — batch : {}", batchId);

        runSafely("DeduplicationService",     () -> deduplicationService.removeBatch(batchId));
        runSafely("TextDeduplicationService", () -> textDeduplicationService.removeBatch(batchId));
        runSafely("EmbeddingCache",           () -> embeddingCache.removeBatch(batchId));

        log.info("✅ [Redis] Nettoyage batch terminé : {}", batchId);
    }

    // -------------------------------------------------------------------------
    // Nettoyage global
    // -------------------------------------------------------------------------

    /**
     * Nettoie l'intégralité des caches Redis (tous les batches).
     */
    public void cleanupAll() {
        log.info("🧹 [Redis] Nettoyage GLOBAL de tous les caches");

        runSafely("DeduplicationService",     deduplicationService::clearAll);
        runSafely("TextDeduplicationService", textDeduplicationService::clearAll);
        runSafely("EmbeddingCache",           embeddingCache::clearAll);
        cleanupRateLimits();

        log.info("✅ [Redis] Nettoyage GLOBAL terminé");
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private void cleanupRateLimits() {
        try {
            Set<String> keys = redisTemplate.keys(EmbeddingRepositoryConstants.RATE_LIMIT_KEY_PATTERN);
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                log.info("✅ {} rate-limit(s) supprimé(s)", deleted);
            } else {
                log.info("ℹ️ Aucun rate-limit à supprimer");
            }
        } catch (Exception e) {
            log.warn("⚠️ Erreur nettoyage rate-limits (non bloquant) : {}", e.getMessage());
        }
    }

    /**
     * Exécute une opération de nettoyage sans laisser une erreur
     * bloquer les suivantes.
     */
    private void runSafely(String serviceName, Runnable action) {
        try {
            action.run();
            log.info("✅ {} nettoyé", serviceName);
        } catch (Exception e) {
            log.error("❌ Erreur nettoyage {} : {}", serviceName, e.getMessage(), e);
        }
    }
}