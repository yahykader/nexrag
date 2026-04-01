package com.exemple.nexrag.service.rag.cache.metrics;

import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Service de cache Redis pour embeddings et résultats de requêtes.
 *
 * Principe SRP  : unique responsabilité → gérer le cycle de vie
 *                 des données en cache Redis.
 * Clean code    : {@code hash()} utilise SHA-256 au lieu de
 *                 {@code hashCode()} Java qui produit des collisions.
 *                 {@code clear()} utilise {@code keys + delete}
 *                 au lieu d'une connexion bas niveau non sécurisée.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Service
public class CacheService {

    private static final Duration TTL_DEFAULT   = Duration.ofHours(24);
    private static final Duration TTL_EMBEDDING = Duration.ofDays(7);
    private static final Duration TTL_QUERY     = Duration.ofHours(1);

    private static final String PREFIX_EMBEDDING = "embedding:";
    private static final String PREFIX_QUERY     = "query:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RAGMetrics                    ragMetrics;

    public CacheService(
            @Qualifier("redisTemplateJson") RedisTemplate<String, Object> redisTemplate,
            RAGMetrics ragMetrics) {

        this.redisTemplate = redisTemplate;
        this.ragMetrics    = ragMetrics;
    }

    // -------------------------------------------------------------------------
    // API générique
    // -------------------------------------------------------------------------

    /**
     * Récupère une valeur du cache.
     *
     * @param key  clé Redis
     * @param type type attendu
     * @return valeur si présente, {@link Optional#empty()} sinon
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            @SuppressWarnings("unchecked")
            T value = (T) redisTemplate.opsForValue().get(key);

            if (value != null) {
                ragMetrics.recordCacheHit(cacheType(key));
                log.debug("✅ Cache HIT : {}", key);
                return Optional.of(value);
            }

            ragMetrics.recordCacheMiss(cacheType(key));
            log.debug("❌ Cache MISS : {}", key);
            return Optional.empty();

        } catch (Exception e) {
            log.error("❌ Cache get error : {}", key, e);
            ragMetrics.recordCacheMiss(cacheType(key));
            return Optional.empty();
        }
    }

    /** Stocke une valeur avec le TTL par défaut (24h). */
    public void put(String key, Object value) {
        put(key, value, TTL_DEFAULT);
    }

    /** Stocke une valeur avec un TTL personnalisé. */
    public void put(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            log.debug("💾 Cache SET : {} (TTL={})", key, ttl);
        } catch (Exception e) {
            log.error("❌ Cache set error : {}", key, e);
        }
    }

    /** Supprime une clé du cache. */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("🗑️ Cache DELETE : {}", key);
        } catch (Exception e) {
            log.error("❌ Cache delete error : {}", key, e);
        }
    }

    /** Vérifie si une clé existe dans le cache. */
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("❌ Cache exists error : {}", key, e);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // API spécialisée — embeddings
    // -------------------------------------------------------------------------

    /** Met en cache un embedding pour un texte donné (TTL : 7 jours). */
    public void cacheEmbedding(String text, Object embedding) {
        put(PREFIX_EMBEDDING + hash(text), embedding, TTL_EMBEDDING);
    }

    /** Récupère l'embedding d'un texte depuis le cache. */
    public <T> Optional<T> getEmbedding(String text, Class<T> type) {
        return get(PREFIX_EMBEDDING + hash(text), type);
    }

    // -------------------------------------------------------------------------
    // API spécialisée — résultats de requêtes
    // -------------------------------------------------------------------------

    /** Met en cache le résultat d'une requête (TTL : 1 heure). */
    public void cacheQueryResult(String query, Object result) {
        put(PREFIX_QUERY + hash(query), result, TTL_QUERY);
    }

    /** Récupère le résultat d'une requête depuis le cache. */
    public <T> Optional<T> getQueryResult(String query, Class<T> type) {
        return get(PREFIX_QUERY + hash(query), type);
    }

    // -------------------------------------------------------------------------
    // Nettoyage
    // -------------------------------------------------------------------------

    /**
     * Vide les clés RAG du cache.
     *
     * Clean code : utilise {@code keys(pattern) + delete(keys)} au lieu
     * d'une connexion bas niveau {@code flushAll()} qui effacerait
     * toutes les données Redis du serveur (y compris celles d'autres services).
     */
    public void clear() {
        try {
            Set<String> keys = redisTemplate.keys("embedding:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            Set<String> queryKeys = redisTemplate.keys("query:*");
            if (queryKeys != null && !queryKeys.isEmpty()) {
                redisTemplate.delete(queryKeys);
            }
            log.warn("⚠️ Cache RAG vidé (embeddings + queries)");
        } catch (Exception e) {
            log.error("❌ Cache clear error", e);
        }
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private String cacheType(String key) {
        if (key.startsWith(PREFIX_EMBEDDING)) return "embedding";
        if (key.startsWith(PREFIX_QUERY))     return "query";
        return "other";
    }

    /**
     * Hash SHA-256 d'une chaîne.
     *
     * Fix : {@code String.hashCode()} produit des collisions fréquentes
     * (espace de 32 bits). SHA-256 garantit une distribution uniforme
     * sur 256 bits — adapté aux clés de cache.
     */
    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti présent dans tout JDK — ne se produit pas
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }
}