package com.exemple.nexrag.service.rag.ingestion.deduplication.file;

import com.exemple.nexrag.constant.DeduplicationRedisKeys;
import com.exemple.nexrag.dto.deduplication.file.DeduplicationStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Accès Redis pour la déduplication de fichiers.
 *
 * Principe SRP : unique responsabilité → lire et écrire dans Redis.
 *                Aucune logique métier ici — juste les opérations CRUD Redis.
 * Clean code   : centralise toutes les interactions Redis, élimine l'accès
 *                direct à {@code redisTemplate} depuis {@link DeduplicationService}.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeduplicationStore {

    private final RedisTemplate<String, String> redisTemplate;

    // -------------------------------------------------------------------------
    // Lecture
    // -------------------------------------------------------------------------

    /**
     * Vérifie si un hash est déjà enregistré.
     */
    public boolean exists(String hash) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(DeduplicationRedisKeys.forHash(hash)));
    }

    /**
     * Retourne le batchId associé à un hash, ou {@code null} s'il n'existe pas.
     */
    public String getBatchId(String hash) {
        return redisTemplate.opsForValue().get(DeduplicationRedisKeys.forHash(hash));
    }

    /**
     * Retourne le TTL restant d'un hash (en secondes), ou {@code -1} si absent.
     */
    public long getTtlSeconds(String hash) {
        Long ttl = redisTemplate.getExpire(DeduplicationRedisKeys.forHash(hash), TimeUnit.SECONDS);
        return ttl != null ? ttl : -1;
    }

    // -------------------------------------------------------------------------
    // Écriture
    // -------------------------------------------------------------------------

    /**
     * Enregistre un hash associé à un batchId avec TTL.
     */
    public void save(String hash, String batchId, long ttl, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(
            DeduplicationRedisKeys.forHash(hash),
            batchId,
            ttl,
            timeUnit
        );
    }

    /**
     * Rafraîchit le TTL d'un hash existant.
     *
     * @return {@code true} si le TTL a été mis à jour
     */
    public boolean refreshTtl(String hash, long ttl, TimeUnit timeUnit) {
        return Boolean.TRUE.equals(
            redisTemplate.expire(DeduplicationRedisKeys.forHash(hash), ttl, timeUnit)
        );
    }

    // -------------------------------------------------------------------------
    // Suppression
    // -------------------------------------------------------------------------

    /**
     * Supprime le hash d'un fichier spécifique.
     */
    public boolean delete(String hash) {
        return Boolean.TRUE.equals(redisTemplate.delete(DeduplicationRedisKeys.forHash(hash)));
    }

    /**
     * Supprime toutes les clés de hash appartenant à un batchId.
     *
     * @return nombre de clés supprimées
     */
    public int deleteByBatchId(String batchId) {
        Set<String> keys = redisTemplate.keys(DeduplicationRedisKeys.HASH_PATTERN);
        if (keys == null || keys.isEmpty()) return 0;

        int deleted = 0;
        for (String key : keys) {
            if (batchId.equals(redisTemplate.opsForValue().get(key))) {
                if (Boolean.TRUE.equals(redisTemplate.delete(key))) {
                    deleted++;
                    log.debug("🗑️ [Redis] Clé supprimée : {}", key);
                }
            }
        }
        return deleted;
    }

    /**
     * Supprime toutes les clés de hash ({@code ingestion:hash:*}).
     *
     * @return nombre de clés supprimées
     */
    public int deleteAll() {
        Set<String> keys = redisTemplate.keys(DeduplicationRedisKeys.HASH_PATTERN);
        if (keys == null || keys.isEmpty()) return 0;
        Long deleted = redisTemplate.delete(keys);
        return deleted != null ? deleted.intValue() : 0;
    }

    // -------------------------------------------------------------------------
    // Statistiques
    // -------------------------------------------------------------------------

    /**
     * Compte le nombre de fichiers trackés.
     */
    public long count() {
        Set<String> keys = redisTemplate.keys(DeduplicationRedisKeys.HASH_PATTERN);
        return keys != null ? keys.size() : 0;
    }

    /**
     * Vérifie la disponibilité de Redis.
     */
    public boolean isAvailable() {
        try {
            redisTemplate.opsForValue().get("ping");
            return true;
        } catch (Exception e) {
            log.error("❌ [Redis] Inaccessible : {}", e.getMessage());
            return false;
        }
    }
}