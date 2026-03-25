package com.exemple.nexrag.service.rag.ingestion;

import com.exemple.nexrag.config.ClamAvProperties;
import com.exemple.nexrag.exception.DuplicateFileException;
import com.exemple.nexrag.exception.VirusDetectedException;
import com.exemple.nexrag.service.rag.ingestion.deduplication.file.DeduplicationService;
import com.exemple.nexrag.dto.*;
import com.exemple.nexrag.service.rag.ingestion.repository.EmbeddingRepository;
import com.exemple.nexrag.service.rag.ingestion.security.AntivirusGuard;
import com.exemple.nexrag.service.rag.ingestion.security.AntivirusScanner;
import com.exemple.nexrag.service.rag.ingestion.strategy.IngestionStrategy;
import com.exemple.nexrag.service.rag.ingestion.tracker.IngestionTracker;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrateur principal d'ingestion multimodale.
 *
 * Principe SRP  : orchestre les étapes — antivirus, dédup, sélection stratégie,
 *                 ingestion, métriques, rollback. Chaque étape délègue à un service dédié.
 * Principe OCP  : l'ajout d'une nouvelle stratégie ne modifie pas cet orchestrateur.
 * Principe DIP  : dépend des interfaces {@link IngestionStrategy}, {@link AntivirusGuard},
 *                 {@link DeduplicationService} — pas de leurs implémentations concrètes.
 * Clean code    : inner classes extraites dans {@code model/}.
 *                 {@code AntivirusGuard.assertClean()} remplace le fichier temporaire manuel.
 *                 {@code IngestionStatus} devient un record immuable.
 *
 * @author RAG Team
 * @version 2.0
 */
@Slf4j
@Service
public class IngestionOrchestrator {

    private final List<IngestionStrategy> strategies;
    private final RAGMetrics             ragMetrics;
    private final IngestionTracker       tracker;
    private final DeduplicationService   deduplicationService;
    private final AntivirusGuard         antivirusGuard;
    private final AntivirusScanner       antivirusScanner;
    private final EmbeddingRepository    embeddingRepository;
    private final ClamAvProperties       antivirusProps;

    private final Map<String, IngestionStatus> activeIngestions = new ConcurrentHashMap<>();

    public IngestionOrchestrator(
            List<IngestionStrategy> strategies,
            RAGMetrics             ragMetrics,
            IngestionTracker       tracker,
            DeduplicationService   deduplicationService,
            AntivirusGuard         antivirusGuard,
            AntivirusScanner       antivirusScanner,
            EmbeddingRepository    embeddingRepository,
            ClamAvProperties       antivirusProps) {

        this.strategies          = strategies;
        this.ragMetrics          = ragMetrics;
        this.tracker             = tracker;
        this.deduplicationService = deduplicationService;
        this.antivirusGuard      = antivirusGuard;
        this.antivirusScanner    = antivirusScanner;
        this.embeddingRepository = embeddingRepository;
        this.antivirusProps      = antivirusProps;

        // ✅ strategies déjà triées par IngestionConfig — sort() supprimé
        log.info("✅ IngestionOrchestrator initialisé — {} stratégies | antivirus: {}",
            strategies.size(), antivirusProps.isEnabled() ? "ON" : "OFF");
    }

    // -------------------------------------------------------------------------
    // API synchrone
    // -------------------------------------------------------------------------

    public IngestionResult ingestFile(MultipartFile file, String batchId) throws Exception {
        return ingestFileInternal(file, batchId);
    }

    // -------------------------------------------------------------------------
    // API asynchrone — fichier unique
    // -------------------------------------------------------------------------

    @Async
    public CompletableFuture<IngestionResult> ingestFileAsync(MultipartFile file, String batchId) {
        try {
            log.info("📥 [ASYNC] Démarrage : {} (batch: {})", file.getOriginalFilename(), batchId);
            IngestionResult result = ingestFileInternal(file, batchId);
            log.info("✅ [ASYNC] Terminé : {} — text={} images={}",
                file.getOriginalFilename(), result.textEmbeddings(), result.imageEmbeddings());
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("❌ [ASYNC] Erreur : {}", file.getOriginalFilename(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // -------------------------------------------------------------------------
    // API asynchrone — batch simple
    // -------------------------------------------------------------------------

    @Async
    public CompletableFuture<List<IngestionResult>> ingestBatch(
            List<MultipartFile> files, String batchId) {

        log.info("📦 [BATCH] Démarrage : {} fichiers (batch: {})", files.size(), batchId);
        long start   = System.currentTimeMillis();
        List<IngestionResult> results = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                try {
                    results.add(ingestFileInternal(file, batchId));
                } catch (Exception e) {
                    log.error("❌ [BATCH] Erreur : {}", file.getOriginalFilename(), e);
                }
            }

            long duration   = System.currentTimeMillis() - start;
            int  totalText  = results.stream().mapToInt(IngestionResult::textEmbeddings).sum();
            int  totalImages = results.stream().mapToInt(IngestionResult::imageEmbeddings).sum();

            log.info("✅ [BATCH] Terminé : {}/{} succès, {}ms, text={} images={}",
                results.size(), files.size(), duration, totalText, totalImages);

            return CompletableFuture.completedFuture(results);

        } catch (Exception e) {
            log.error("❌ [BATCH] Erreur globale", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // -------------------------------------------------------------------------
    // API asynchrone — batch détaillé
    // -------------------------------------------------------------------------

    @Async
    public CompletableFuture<BatchIngestionResult> ingestBatchDetailed(
            List<MultipartFile> files, String batchId) {

        log.info("📦 [BATCH-DETAILED] Démarrage : {} fichiers", files.size());
        long start = System.currentTimeMillis();

        List<FileIngestionResult> fileResults = files.stream()
            .map(f -> processFileForBatch(f, batchId))
            .toList();

        long duration      = System.currentTimeMillis() - start;
        int  successCount  = (int) fileResults.stream().filter(FileIngestionResult::success).count();
        int  duplicateCount = (int) fileResults.stream().filter(FileIngestionResult::duplicate).count();
        int  totalText     = fileResults.stream()
            .filter(r -> r.success() && r.result() != null)
            .mapToInt(r -> r.result().textEmbeddings()).sum();
        int  totalImages   = fileResults.stream()
            .filter(r -> r.success() && r.result() != null)
            .mapToInt(r -> r.result().imageEmbeddings()).sum();

        log.info("✅ [BATCH-DETAILED] {} succès, {} doublons, {} erreurs, {}ms",
            successCount, duplicateCount,
            files.size() - successCount - duplicateCount, duration);

        return CompletableFuture.completedFuture(new BatchIngestionResult(
            batchId, fileResults, successCount,
            files.size() - successCount - duplicateCount,
            totalText, totalImages, duration, duplicateCount
        ));
    }

    // -------------------------------------------------------------------------
    // Logique d'ingestion interne
    // -------------------------------------------------------------------------

    private IngestionResult ingestFileInternal(MultipartFile file, String batchId)
            throws Exception {

        String filename  = file.getOriginalFilename();
        String extension = getExtension(filename);

        log.info("📄 Ingestion : {} ({}, {} KB, batch: {})",
            filename, extension.toUpperCase(), file.getSize() / 1024, batchId);

        ragMetrics.startIngestion();

        IngestionStatus status = IngestionStatus.started(batchId, filename);
        activeIngestions.put(batchId, status);

        long   startTime    = System.currentTimeMillis();
        String strategyName = "unknown";

        try {
            // 0. ANTIVIRUS — délégue à AntivirusGuard (plus de fichier temporaire manuel)
            if (antivirusProps.isEnabled()) {
                antivirusGuard.assertClean(file);
                log.debug("✅ Fichier sain : {}", filename);
            }

            // 1. SÉLECTION STRATÉGIE
            IngestionStrategy strategy = selectStrategy(file, extension);
            if (strategy == null) {
                throw new UnsupportedOperationException("Aucune stratégie pour : " + extension);
            }

            strategyName = strategy.getName();
            status       = status.withStrategy(strategyName);
            activeIngestions.put(batchId, status);

            log.info("🎯 Stratégie : {} (priorité: {})", strategyName, strategy.getPriority());

            // 2. DÉDUPLICATION
            byte[] fileBytes = file.getBytes();
            String fileHash  = deduplicationService.computeHash(fileBytes);

            if (deduplicationService.isDuplicateAndRecord(fileHash, strategyName)) {
                String existingBatchId = deduplicationService.getExistingBatchId(fileHash);
                ragMetrics.recordDuplicate(strategyName);
                log.warn("⚠️ Doublon détecté : {} (batch existant: {})", filename, existingBatchId);
                throw new DuplicateFileException("Fichier en doublon : " + filename, existingBatchId);
            }

            deduplicationService.markAsIngested(fileHash, batchId);

            // 3. INGESTION
            IngestionResult result = strategy.ingest(file, batchId);

            // 4. SUCCÈS — métriques
            long duration       = System.currentTimeMillis() - startTime;
            int  totalEmbeddings = result.totalEmbeddings();

            ragMetrics.recordStrategyProcessing(strategyName, duration, result.textEmbeddings());
            ragMetrics.recordIngestionSuccess(strategyName, duration, totalEmbeddings);

            status = status.completed(true, duration);
            activeIngestions.put(batchId, status);

            log.info("✅ Succès : {} — strategy={} text={} images={} {}ms",
                filename, strategyName,
                result.textEmbeddings(), result.imageEmbeddings(), duration);

            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            status = status.completed(false, duration);
            activeIngestions.put(batchId, status);

            if (e instanceof DuplicateFileException dup) {
                log.warn("⚠️ Doublon : {} (batch existant: {})", filename, dup.getExistingBatchId());
            } else {
                log.error("❌ Échec : {} — rollback en cours...", filename, e);
                rollbackSafely(batchId);
                ragMetrics.recordIngestionError(strategyName, e.getClass().getSimpleName());
            }

            throw e;

        } finally {
            ragMetrics.endIngestion();
            scheduleCleanup(batchId);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers privés
    // -------------------------------------------------------------------------

    private FileIngestionResult processFileForBatch(MultipartFile file, String batchId) {
        try {
            IngestionResult result = ingestFileInternal(file, batchId);
            return FileIngestionResult.ok(file.getOriginalFilename(), result);

        } catch (DuplicateFileException e) {
            log.warn("⚠️ [BATCH] Doublon : {} (batch: {})",
                file.getOriginalFilename(), e.getExistingBatchId());
            return FileIngestionResult.duplicate(file.getOriginalFilename(), e.getExistingBatchId());

        } catch (Exception e) {
            log.error("❌ [BATCH] Échec : {}", file.getOriginalFilename(), e);
            return FileIngestionResult.error(file.getOriginalFilename(), e.getMessage());
        }
    }

    private IngestionStrategy selectStrategy(MultipartFile file, String extension) {
        return strategies.stream()
            .filter(s -> s.canHandle(file, extension))
            .findFirst()
            .orElse(null);
    }

    private void rollbackSafely(String batchId) {
        try {
            int rolledBack = tracker.rollbackBatch(batchId);
            log.info("🔄 Rollback : {} embeddings supprimés", rolledBack);
        } catch (Exception e) {
            log.error("❌ Erreur rollback : {}", e.getMessage());
        }
    }

    private void scheduleCleanup(String batchId) {
        CompletableFuture.delayedExecutor(60, TimeUnit.SECONDS)
            .execute(() -> activeIngestions.remove(batchId));
    }

    private String getExtension(String filename) {
        if (filename == null || filename.isBlank()) return "unknown";
        int dot = filename.lastIndexOf('.');
        return (dot == -1 || dot == filename.length() - 1)
            ? "unknown"
            : filename.substring(dot + 1).toLowerCase();
    }

    // -------------------------------------------------------------------------
    // API publique — déduplication
    // -------------------------------------------------------------------------

    public boolean fileExists(MultipartFile file) {
        try {
            String hash = deduplicationService.computeHash(file.getBytes());
            return deduplicationService.isDuplicateByHash(hash);
        } catch (Exception e) {
            log.error("❌ Vérification existence fichier : {}", file.getOriginalFilename(), e);
            return false;
        }
    }

    public String getExistingBatchId(MultipartFile file) {
        try {
            String hash = deduplicationService.computeHash(file.getBytes());
            return deduplicationService.isDuplicateByHash(hash)
                ? deduplicationService.getExistingBatchId(hash)
                : null;
        } catch (Exception e) {
            log.error("❌ Récupération batchId existant : {}", file.getOriginalFilename(), e);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // API publique — monitoring
    // -------------------------------------------------------------------------

    public List<IngestionStatus> getActiveIngestions() {
        return List.copyOf(activeIngestions.values());
    }

    public Optional<IngestionStatus> getIngestionStatus(String batchId) {
        return Optional.ofNullable(activeIngestions.get(batchId));
    }

    public StatsResponse getStats() {
        return new StatsResponse(
            strategies.size(),
            activeIngestions.size(),
            tracker.getBatchCount(),
            tracker.getTotalEmbeddings(),
            ragMetrics.getActiveIngestions()
        );
    }

    public void logStats() {
        StatsResponse s = getStats();
        log.info("📊 Stats — stratégies: {} | ingestions actives: {} | batches: {} | embeddings: {}",
            s.strategiesCount(), s.activeIngestions(), s.trackerBatches(), s.trackerEmbeddings());
    }

    public DetailedHealthResponse getHealthReport() {
        boolean redisOk     = deduplicationService.isHealthy();
        boolean antivirusOk = !antivirusProps.isEnabled() || antivirusScanner.isAvailable();
        StatsResponse stats  = getStats();

        String status = redisOk && antivirusOk
            ? (stats.activeIngestions() > 50 ? "OVERLOADED" : "HEALTHY")
            : "DEGRADED";

        return new DetailedHealthResponse(status, strategies.size(), stats.activeIngestions(),
            stats.trackerBatches(), redisOk, antivirusOk, antivirusProps.isEnabled());
    }

    public List<String> getAvailableStrategies() {
        return strategies.stream().map(IngestionStrategy::getName).toList();
    }

    public List<StrategyInfo> getStrategiesInfo() {
        return strategies.stream()
            .map(s -> new StrategyInfo(s.getName(), s.getPriority(), s.getClass().getSimpleName()))
            .toList();
    }
}