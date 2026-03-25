package com.exemple.nexrag.service.rag.ingestion.cache;

import com.exemple.nexrag.dto.cache.EmbeddingCacheProperties;
import com.exemple.nexrag.constant.EmbeddingCacheRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Accès Redis pour le cache d'embeddings.
 *
 * Principe SRP : unique responsabilité → lire et écrire dans Redis.
 *                Aucune logique métier — uniquement des opérations CRUD Redis.
 * Clean code   : élimine les accès directs à {@code redisTemplate}
 *                éparpillés dans {@link EmbeddingCache}.
 *
 * @author RAG Team
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingCacheStore {

    private final RedisTemplate<String, String> redisTemplate;

    // -------------------------------------------------------------------------
    // Lecture
    // -------------------------------------------------------------------------

    public String get(String textHash) {
        return redisTemplate.opsForValue().get(EmbeddingCacheRedisKeys.forHash(textHash));
    }

    public boolean exists(String textHash) {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey(EmbeddingCacheRedisKeys.forHash(textHash))
        );
    }

    // -------------------------------------------------------------------------
    // Écriture
    // -------------------------------------------------------------------------

    public void save(String textHash, String serialized, int ttlHours) {
        redisTemplate.opsForValue().set(
            EmbeddingCacheRedisKeys.forHash(textHash),
            serialized,
            ttlHours,
            TimeUnit.HOURS
        );
    }

    /**
     * Associe un hash d'embedding à un batch (via Redis Set).
     */
    public void trackBatch(String batchId, String textHash, int ttlHours) {
        if (batchId == null || batchId.isBlank()) return;
        String batchKey = EmbeddingCacheRedisKeys.forBatch(batchId);
        redisTemplate.opsForSet().add(batchKey, textHash);
        redisTemplate.expire(batchKey, ttlHours, TimeUnit.HOURS);
    }

    // -------------------------------------------------------------------------
    // Suppression
    // -------------------------------------------------------------------------

    public void delete(String textHash) {
        redisTemplate.delete(EmbeddingCacheRedisKeys.forHash(textHash));
    }

    /**
     * Supprime tous les embeddings associés à un batch.
     *
     * @return nombre de clés supprimées
     */
    public int deleteByBatchId(String batchId) {
        String      batchKey = EmbeddingCacheRedisKeys.forBatch(batchId);
        Set<String> hashes   = redisTemplate.opsForSet().members(batchKey);

        if (hashes == null || hashes.isEmpty()) return 0;

        int deleted = 0;
        for (String hash : hashes) {
            if (Boolean.TRUE.equals(redisTemplate.delete(EmbeddingCacheRedisKeys.forHash(hash)))) {
                deleted++;
            }
        }
        redisTemplate.delete(batchKey);
        return deleted;
    }

    /**
     * Supprime toutes les clés {@code emb:*} et {@code batch:emb:*}.
     *
     * @return nombre total de clés supprimées
     */
    public int deleteAll() {
        int total = 0;
        total += deletePattern(EmbeddingCacheRedisKeys.EMB_PREFIX   + "*");
        total += deletePattern(EmbeddingCacheRedisKeys.BATCH_PREFIX + "*");
        return total;
    }

    // -------------------------------------------------------------------------
    // Statistiques
    // -------------------------------------------------------------------------

    public long countBatch(String batchId) {
        Long size = redisTemplate.opsForSet().size(EmbeddingCacheRedisKeys.forBatch(batchId));
        return size != null ? size : 0;
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private int deletePattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) return 0;
            Long deleted = redisTemplate.delete(keys);
            int  count   = deleted != null ? deleted.intValue() : 0;
            log.info("✅ [Cache] Pattern '{}' : {} clé(s) supprimée(s)", pattern, count);
            return count;
        } catch (Exception e) {
            log.error("❌ [Cache] Erreur suppression pattern '{}' : {}", pattern, e.getMessage());
            return 0;
        }
    }
}