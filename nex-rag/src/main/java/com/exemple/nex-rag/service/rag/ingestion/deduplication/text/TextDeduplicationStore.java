package com.exemple.nexrag.service.rag.ingestion.deduplication.text;

import com.exemple.nexrag.constant.TextDeduplicationRedisKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Accès Redis pour la déduplication de textes.
 *
 * Principe SRP : unique responsabilité → lire et écrire dans Redis.
 *                Aucune logique métier — juste les opérations CRUD Redis.
 * Clean code   : centralise les accès Redis, supprime les try/catch
 *                dupliqués dans chaque méthode du service.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextDeduplicationStore {

    private final RedisTemplate<String, String> redisTemplate;

    // -------------------------------------------------------------------------
    // Lecture
    // -------------------------------------------------------------------------

    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.debug("⚠️ [Redis] Lecture impossible (fallback local) : {}", e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Écriture
    // -------------------------------------------------------------------------

    /**
     * Marque une clé comme indexée avec TTL.
     * Non bloquant : les erreurs Redis sont absorbées (le cache local fait foi).
     */
    public void markIndexed(String key, int ttlDays) {
        try {
            redisTemplate.opsForValue().set(
                key,
                TextDeduplicationRedisKeys.INDEXED_VALUE,
                Duration.ofDays(ttlDays)
            );
        } catch (Exception e) {
            log.debug("⚠️ [Redis] Marquage impossible : {}", e.getMessage());
        }
    }

    /**
     * Associe un hash de texte à un batch (via Redis Set).
     */
    public void trackBatchHash(String batchId, String hash, int ttlDays) {
        try {
            String batchKey = TextDeduplicationRedisKeys.forBatch(batchId);
            redisTemplate.opsForSet().add(batchKey, hash);
            redisTemplate.expire(batchKey, Duration.ofDays(ttlDays));
        } catch (Exception e) {
            log.debug("⚠️ [Redis] Tracking batch impossible : {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Suppression
    // -------------------------------------------------------------------------

    /**
     * Supprime tous les hashs de texte associés à un batch.
     *
     * @return nombre de clés supprimées
     */
    public int deleteByBatchId(String batchId, boolean batchIdScope) {
        try {
            String batchKey = TextDeduplicationRedisKeys.forBatch(batchId);
            Set<String> hashes = redisTemplate.opsForSet().members(batchKey);

            if (hashes == null || hashes.isEmpty()) {
                log.debug("ℹ️ [Redis] Aucun hash texte pour batch : {}", batchId);
                return 0;
            }

            int deleted = 0;
            for (String hash : hashes) {
                String key = batchIdScope
                    ? TextDeduplicationRedisKeys.forHashInBatch(batchId, hash)
                    : TextDeduplicationRedisKeys.forHash(hash);

                if (Boolean.TRUE.equals(redisTemplate.delete(key))) {
                    deleted++;
                }
            }

            redisTemplate.delete(batchKey);
            return deleted;

        } catch (Exception e) {
            log.error("❌ [Redis] Erreur suppression batch texte : {}", batchId, e);
            return 0;
        }
    }

    /**
     * Supprime toutes les clés {@code text:dedup:*} et {@code batch:text:*}.
     *
     * @return nombre total de clés supprimées
     */
    public int deleteAll() {
        int total = 0;
        total += deletePattern(TextDeduplicationRedisKeys.DEDUP_PREFIX + "*");
        total += deletePattern(TextDeduplicationRedisKeys.BATCH_PREFIX + "*");
        return total;
    }

    // -------------------------------------------------------------------------
    // Statistiques
    // -------------------------------------------------------------------------

    public long countByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("❌ [Redis] Erreur comptage pattern '{}' : {}", pattern, e.getMessage());
            return 0;
        }
    }

    public long countBatchHashes(String batchId) {
        try {
            Long size = redisTemplate.opsForSet().size(
                TextDeduplicationRedisKeys.forBatch(batchId)
            );
            return size != null ? size : 0;
        } catch (Exception e) {
            log.error("❌ [Redis] Erreur comptage batch '{}' : {}", batchId, e.getMessage());
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private int deletePattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) return 0;
            Long deleted = redisTemplate.delete(keys);
            int count = deleted != null ? deleted.intValue() : 0;
            log.info("✅ [Redis] Pattern '{}' : {} clé(s) supprimée(s)", pattern, count);
            return count;
        } catch (Exception e) {
            log.error("❌ [Redis] Erreur suppression pattern '{}' : {}", pattern, e.getMessage());
            return 0;
        }
    }
}