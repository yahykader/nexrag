package com.exemple.nexrag.service.rag.ingestion.deduplication.text;

import com.exemple.nexrag.service.rag.ingestion.cache.CacheCleanable;
import com.exemple.nexrag.constant.TextDeduplicationRedisKeys;
import com.exemple.nexrag.service.rag.ingestion.deduplication.text.TextDeduplicationStore;
import com.exemple.nexrag.service.rag.ingestion.deduplication.text.TextNormalizer;
import com.exemple.nexrag.service.rag.ingestion.deduplication.text.TextLocalCache;
import com.exemple.nexrag.dto.deduplication.text.TextDeduplicationProperties;
import com.exemple.nexrag.dto.deduplication.text.DedupStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service de déduplication des textes avant insertion dans PgVector.
 *
 * Principe SRP  : unique responsabilité → orchestrer la déduplication de textes.
 *                 Le hachage est dans {@link TextNormalizer}.
 *                 Le cache local est dans {@link TextLocalCache}.
 *                 Les accès Redis sont dans {@link TextDeduplicationStore}.
 *                 La configuration est dans {@link TextDeduplicationProperties}.
 * Clean code    : supprime {@code clearBatch}/{@code removeBatch} (deux méthodes
 *                 identiques), supprime {@code truncate()} (dupliqué partout),
 *                 supprime la magic string "1" → {@link TextDeduplicationRedisKeys#INDEXED_VALUE}.
 *
 * Stratégie de déduplication :
 * 1. Hash SHA-256 du texte normalisé
 * 2. Vérification ET marquage ATOMIQUE dans le cache local (ConcurrentHashMap)
 * 3. Synchronisation avec Redis en arrière-plan (non bloquant)
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextDeduplicationService implements CacheCleanable{

    private final TextDeduplicationProperties props;
    private final TextNormalizer              normalizer;
    private final TextLocalCache              localCache;
    private final TextDeduplicationStore      store;

    // -------------------------------------------------------------------------
    // Vérification / Marquage
    // -------------------------------------------------------------------------

    /**
     * Opération ATOMIQUE : vérifie et marque en une seule opération.
     * Garantit l'absence de race condition grâce au ConcurrentHashMap.
     *
     * @return {@code true} si le texte est nouveau, {@code false} si doublon
     */
    public boolean checkAndMark(String text, String batchId) {
        if (!props.isEnabled() || isBlank(text)) return true;

        String key = buildKey(normalizer.hash(text), batchId);

        // Opération atomique — si add() retourne false, le texte est déjà présent
        if (!localCache.addIfAbsent(key)) {
            log.debug("🔄 [Dedup] Doublon (cache local) : {}", truncate(text));
            return false;
        }

        log.debug("✅ [Dedup] Nouveau texte marqué : {}", truncate(text));
        trackAndPersist(batchId, normalizer.hash(text), key);
        return true;
    }

    /**
     * Vérifie si un texte est doublon (lecture seule, non atomique).
     */
    public boolean isDuplicate(String text, String batchId) {
        if (!props.isEnabled() || isBlank(text)) return false;

        String hash = normalizer.hash(text);
        String key  = buildKey(hash, batchId);

        if (localCache.contains(key)) return true;

        // Fallback Redis
        if (store.exists(key)) {
            localCache.add(key);
            trackIfBatchPresent(batchId, hash);
            return true;
        }

        return false;
    }

    /**
     * Marque un texte comme indexé sans vérification préalable.
     */
    public void markAsIndexed(String text, String batchId) {
        if (!props.isEnabled() || isBlank(text)) return;

        String hash = normalizer.hash(text);
        String key  = buildKey(hash, batchId);

        localCache.add(key);
        trackAndPersist(batchId, hash, key);
        log.debug("✅ [Dedup] Texte marqué : {}", truncate(text));
    }

    // -------------------------------------------------------------------------
    // Nettoyage
    // -------------------------------------------------------------------------

    /**
     * Vide le cache local (à appeler en fin de batch).
     */
    public void clearLocalCache() {
        localCache.clear();
    }

    // -------------------------------------------------------------------------
    // CacheCleanable — nettoyage
    // -------------------------------------------------------------------------
 
    @Override
    public void removeBatch(String batchId) {
        if (!props.isEnabled() || isBlank(batchId)) return;
        int deleted = store.deleteByBatchId(batchId, props.isBatchIdScope());
        log.info("✅ [Dedup] Batch texte supprimé : {} ({} hash(es))", batchId, deleted);
    }
 
    @Override
    public void clearAll() {
        if (!props.isEnabled()) return;
        log.warn("🚨 [Dedup] SUPPRESSION GLOBALE des hashs texte");
        int deleted = store.deleteAll();
        localCache.clear();
        log.warn("✅ [Dedup] SUPPRESSION GLOBALE : {} clé(s) Redis", deleted);
    }

    // -------------------------------------------------------------------------
    // Statistiques
    // -------------------------------------------------------------------------

    public long countBatchHashes(String batchId) {
        if (!props.isEnabled() || isBlank(batchId)) return 0;
        return store.countBatchHashes(batchId);
    }

    public DedupStats getStats(String batchId) {
        String pattern = props.isBatchIdScope() && !isBlank(batchId)
            ? TextDeduplicationRedisKeys.DEDUP_PREFIX + batchId + ":*"
            : TextDeduplicationRedisKeys.DEDUP_PREFIX + "*";

        long totalIndexed = store.countByPattern(pattern);
        return new DedupStats(props.isEnabled(), totalIndexed, localCache.size(), props.isBatchIdScope());
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private String buildKey(String hash, String batchId) {
        if (props.isBatchIdScope() && !isBlank(batchId)) {
            return TextDeduplicationRedisKeys.forHashInBatch(batchId, hash);
        }
        return TextDeduplicationRedisKeys.forHash(hash);
    }

    private void trackAndPersist(String batchId, String hash, String key) {
        trackIfBatchPresent(batchId, hash);
        store.markIndexed(key, props.getTtlDays());
    }

    private void trackIfBatchPresent(String batchId, String hash) {
        if (!isBlank(batchId)) {
            store.trackBatchHash(batchId, hash, props.getTtlDays());
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Tronque pour les logs — responsabilité limitée à l'affichage debug.
     */
    private String truncate(String text) {
        return text.length() <= 50 ? text : text.substring(0, 50) + "...";
    }
}