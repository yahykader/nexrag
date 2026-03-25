package com.exemple.nexrag.service.rag.ingestion.deduplication.file;

import com.exemple.nexrag.service.rag.ingestion.cache.CacheCleanable;
import com.exemple.nexrag.constant.DeduplicationRedisKeys;
import com.exemple.nexrag.dto.deduplication.file.DeduplicationStats;
import com.exemple.nexrag.dto.deduplication.file.DuplicationInfo;
import com.exemple.nexrag.dto.deduplication.file.FileInfo;
import com.exemple.nexrag.service.rag.ingestion.deduplication.file.DeduplicationStore;
import com.exemple.nexrag.service.rag.ingestion.deduplication.file.HashComputer;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Service de déduplication de fichiers basé sur hash SHA-256 stocké dans Redis.
 *
 * Principe SRP : unique responsabilité → orchestrer la déduplication de fichiers.
 * Principe DIP : implémente {@link CacheCleanable} — découple le nettoyage
 *                du coordinateur {@link com.exemple.nexrag.service.rag.ingestion.cache.RedisCacheCleanupService}.
 *
 * @author ayahyaoui
 * @version 2.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService implements CacheCleanable {

    private final DeduplicationStore store;
    private final HashComputer       hashComputer;
    private final RAGMetrics         ragMetrics;

    // -------------------------------------------------------------------------
    // Hash
    // -------------------------------------------------------------------------

    public String computeHash(byte[] bytes) {
        return hashComputer.compute(bytes);
    }

    public String computeHash(MultipartFile file) throws IOException {
        return hashComputer.compute(file);
    }

    // -------------------------------------------------------------------------
    // Détection de doublons
    // -------------------------------------------------------------------------

    public boolean isDuplicateByHash(String hash) {
        boolean exists = store.exists(hash);
        if (exists) {
            log.info("⚠️ [Dedup] Doublon détecté — hash={}", hashComputer.toShortHash(hash));
        }
        return exists;
    }

    public boolean isDuplicateAndRecord(String hash, String strategyName) {
        boolean duplicate = isDuplicateByHash(hash);
        if (duplicate) {
            String strategy = (strategyName == null || strategyName.isBlank())
                ? "unknown" : strategyName;
            ragMetrics.recordDuplicate(strategy);
        }
        return duplicate;
    }

    public DuplicationInfo check(MultipartFile file) throws IOException {
        String hash = computeHash(file);
        if (isDuplicateByHash(hash)) {
            return new DuplicationInfo(true, hash, store.getBatchId(hash));
        }
        return new DuplicationInfo(false, hash, null);
    }

    public DuplicationInfo checkAndRecord(MultipartFile file, String strategyName) throws IOException {
        String hash = computeHash(file);
        if (isDuplicateAndRecord(hash, strategyName)) {
            return new DuplicationInfo(true, hash, store.getBatchId(hash));
        }
        return new DuplicationInfo(false, hash, null);
    }

    public String getExistingBatchId(String hash) {
        String batchId = store.getBatchId(hash);
        if (batchId != null && !batchId.isBlank()) {
            log.debug("🔍 [Dedup] BatchId trouvé : {} — hash={}",
                batchId, hashComputer.toShortHash(hash));
        }
        return batchId;
    }

    // -------------------------------------------------------------------------
    // Enregistrement
    // -------------------------------------------------------------------------

    public void markAsIngested(MultipartFile file, String batchId) throws IOException {
        markAsIngested(computeHash(file), batchId);
    }

    public void markAsIngested(String hash, String batchId) {
        markAsIngested(hash, batchId, DeduplicationRedisKeys.DEFAULT_TTL_DAYS, TimeUnit.DAYS);
    }

    public void markAsIngested(String hash, String batchId, long ttl, TimeUnit timeUnit) {
        store.save(hash, batchId, ttl, timeUnit);
        log.debug("✅ [Dedup] Fichier enregistré — hash={}, batchId={}, ttl={}{}",
            hashComputer.toShortHash(hash), batchId, ttl,
            timeUnit.toString().toLowerCase());
    }

    public boolean refreshTtl(String hash, long ttl, TimeUnit timeUnit) {
        boolean refreshed = store.refreshTtl(hash, ttl, timeUnit);
        if (refreshed) {
            log.debug("🔄 [Dedup] TTL rafraîchi — hash={}", hashComputer.toShortHash(hash));
        }
        return refreshed;
    }

    // -------------------------------------------------------------------------
    // CacheCleanable — nettoyage
    // -------------------------------------------------------------------------

    @Override
    public void removeBatch(String batchId) {
        int deleted = store.deleteByBatchId(batchId);
        log.info("✅ [Dedup] Batch supprimé : {} ({} hash(es))", batchId, deleted);
    }

    @Override
    public void clearAll() {
        int deleted = store.deleteAll();
        log.warn("✅ [Dedup] Suppression globale : {} clé(s)", deleted);
    }

    // -------------------------------------------------------------------------
    // Lecture / Statistiques
    // -------------------------------------------------------------------------

    public void removeHash(String hash) {
        if (store.delete(hash)) {
            log.debug("🗑️ [Dedup] Hash supprimé — {}", hashComputer.toShortHash(hash));
        }
    }

    public void removeFile(MultipartFile file) throws IOException {
        removeHash(computeHash(file));
    }

    public FileInfo getFileInfo(String hash) {
        if (!store.exists(hash)) return null;
        return new FileInfo(hash, store.getBatchId(hash), store.getTtlSeconds(hash));
    }

    public DeduplicationStats getStats() {
        return new DeduplicationStats(store.count(), store.isAvailable());
    }

    public boolean isHealthy() {
        return store.isAvailable();
    }

    public boolean performHealthCheck() {
        boolean healthy = store.isAvailable();
        log.debug("{} [Dedup] Health check : {}", healthy ? "✅" : "⚠️", healthy ? "OK" : "FAILED");
        return healthy;
    }
}