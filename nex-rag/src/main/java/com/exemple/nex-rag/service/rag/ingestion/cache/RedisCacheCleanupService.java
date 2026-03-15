package com.exemple.nexrag.service.rag.ingestion.cache;

import com.exemple.nexrag.constant.EmbeddingRepositoryConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Coordinateur de nettoyage de tous les caches Redis du système d'ingestion.
 *
 * Principe SRP : unique responsabilité → coordonner le nettoyage des caches.
 * Principe OCP : ajouter un nouveau cache = créer un bean qui implémente
 *                {@link CacheCleanable}. Cette classe ne change jamais.
 * Principe DIP : dépend de l'abstraction {@link CacheCleanable},
 *                pas des implémentations concrètes.
 *
 * Spring injecte automatiquement TOUS les beans {@link CacheCleanable}
 * dans la liste au démarrage.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheCleanupService {

    /** Tous les caches nettoyables — injectés automatiquement par Spring. */
    private final List<CacheCleanable>          caches;
    private final RedisTemplate<String, String> redisTemplate;

    // -------------------------------------------------------------------------
    // Nettoyage par batch
    // -------------------------------------------------------------------------

    /**
     * Nettoie tous les caches Redis pour un batch spécifique.
     */
    public void cleanupBatch(String batchId) {
        log.info("🧹 [Redis] Nettoyage des caches — batch : {}", batchId);
        caches.forEach(cache -> runSafely(cache.getClass().getSimpleName(),
            () -> cache.removeBatch(batchId)));
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
        caches.forEach(cache -> runSafely(cache.getClass().getSimpleName(),
            cache::clearAll));
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

    private void runSafely(String cacheName, Runnable action) {
        try {
            action.run();
            log.info("✅ {} nettoyé", cacheName);
        } catch (Exception e) {
            log.error("❌ Erreur nettoyage {} : {}", cacheName, e.getMessage(), e);
        }
    }
}