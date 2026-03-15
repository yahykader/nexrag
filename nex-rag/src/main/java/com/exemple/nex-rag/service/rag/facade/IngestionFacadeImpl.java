package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.dto.*;
import com.exemple.nexrag.exception.ResourceNotFoundException;
import com.exemple.nexrag.constant.FileSizeConstants;
import com.exemple.nexrag.dto.*;
import com.exemple.nexrag.validation.FileValidator;
import com.exemple.nexrag.exception.DuplicateFileException;
import com.exemple.nexrag.service.rag.ingestion.IngestionOrchestrator;
import com.exemple.nexrag.service.rag.ingestion.deduplication.file.DeduplicationService;
import com.exemple.nexrag.service.rag.ingestion.progress.ProgressService;
import com.exemple.nexrag.service.rag.ingestion.tracker.IngestionTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Implémentation de la facade d'ingestion.
 *
 * Principe SRP : unique responsabilité → orchestrer les opérations d'ingestion.
 * Principe DIP : dépend des abstractions (Orchestrator, Tracker, etc.).
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionFacadeImpl implements IngestionFacade {

    private static final String STATUS_URL_TEMPLATE = "/api/v1/ingestion/status/";
    private static final String RESULT_URL_TEMPLATE = "/api/v1/ingestion/batch/result/";

    private final IngestionOrchestrator ingestionService;
    private final IngestionTracker      tracker;
    private final DeduplicationService  deduplicationService;
    private final ProgressService       progressService;
    private final FileValidator         fileValidator;
    private final DuplicateChecker      duplicateChecker;

    // -------------------------------------------------------------------------
    // Upload synchrone
    // -------------------------------------------------------------------------

    @Override
    public IngestionResponse uploadSync(MultipartFile file, String batchId) {
        fileValidator.validate(file);
        String resolvedBatchId = resolveOrGenerateBatchId(batchId);

        log.info("📥 Upload sync : {} ({} MB) — batch : {}",
            file.getOriginalFilename(), file.getSize() / 1_000_000, resolvedBatchId);

        long start  = System.currentTimeMillis();
        var  result = executeIngestion(() -> ingestionService.ingestFile(file, resolvedBatchId));
        long duration = System.currentTimeMillis() - start;

        log.info("✅ Ingestion OK : text={} images={} durée={}ms",
            result.textEmbeddings(), result.imageEmbeddings(), duration);

        return IngestionResponse.builder()
            .success(true)
            .batchId(resolvedBatchId)
            .filename(file.getOriginalFilename())
            .fileSize(file.getSize())
            .textEmbeddings(result.textEmbeddings())
            .imageEmbeddings(result.imageEmbeddings())
            .durationMs(duration)
            .streamingUsed(file.getSize() > FileSizeConstants.STREAMING_THRESHOLD)
            .message("Ingestion réussie")
            .duplicate(false)
            .build();
    }

    // -------------------------------------------------------------------------
    // Upload asynchrone
    // -------------------------------------------------------------------------

    @Override
    public AsyncResponse uploadAsync(MultipartFile file, String batchId) {
        fileValidator.validate(file);
        String resolvedBatchId = resolveOrGenerateBatchId(batchId);

        log.info("📥 Upload async : {} — batch : {}", file.getOriginalFilename(), resolvedBatchId);

        // Pré-vérification doublon avant lancement async
        DuplicateSummary preCheck = duplicateChecker.check(List.of(file));
        if (!preCheck.hasNone()) {
            String existingBatchId = preCheck.getExistingBatchIds().get(file.getOriginalFilename());
            return buildDuplicateAsyncResponse(file, existingBatchId);
        }

        launchAsync(file, resolvedBatchId);

        return AsyncResponse.builder()
            .accepted(true)
            .batchId(resolvedBatchId)
            .filename(file.getOriginalFilename())
            .message("Traitement démarré")
            .statusUrl(STATUS_URL_TEMPLATE + resolvedBatchId)
            .duplicate(false)
            .build();
    }

    // -------------------------------------------------------------------------
    // Upload batch
    // -------------------------------------------------------------------------

    @Override
    public BatchResponse uploadBatch(List<MultipartFile> files, String batchId) {
        fileValidator.validateBatch(files);
        String           resolvedBatchId = resolveOrGenerateBatchId(batchId);
        DuplicateSummary duplicates      = duplicateChecker.check(files);
        long             totalSize       = totalSize(files);

        log.info("📥 Batch : {} fichiers ({} MB) — {} doublon(s) — batch : {}",
            files.size(), totalSize / 1_000_000, duplicates.getCount(), resolvedBatchId);

        launchBatchAsync(files, resolvedBatchId, duplicates.getCount());

        return BatchResponse.builder()
            .success(true)
            .batchId(resolvedBatchId)
            .fileCount(files.size())
            .filenames(fileNames(files))
            .totalSize(totalSize)
            .message(batchMessage(duplicates))
            .statusUrl(STATUS_URL_TEMPLATE + resolvedBatchId)
            .duplicates(duplicates)
            .build();
    }

    @Override
    public BatchDetailedResponse uploadBatchDetailed(List<MultipartFile> files, String batchId) {
        fileValidator.validateBatch(files);
        String           resolvedBatchId = resolveOrGenerateBatchId(batchId);
        DuplicateSummary duplicates      = duplicateChecker.check(files);
        long             totalSize       = totalSize(files);

        log.info("📥 Batch détaillé : {} fichiers — {} doublon(s) — batch : {}",
            files.size(), duplicates.getCount(), resolvedBatchId);

        launchBatchDetailedAsync(files, resolvedBatchId, duplicates.getCount());

        return BatchDetailedResponse.builder()
            .accepted(true)
            .success(true)
            .batchId(resolvedBatchId)
            .fileCount(files.size())
            .totalSize(totalSize)
            .message(batchMessage(duplicates))
            .statusUrl(STATUS_URL_TEMPLATE + resolvedBatchId)
            .resultUrl(RESULT_URL_TEMPLATE + resolvedBatchId)
            .duplicates(duplicates)
            .build();
    }

    // -------------------------------------------------------------------------
    // Suivi
    // -------------------------------------------------------------------------

    @Override
    public StatusResponse getStatus(String batchId) {
        var textIds  = tracker.getTextEmbeddingIds(batchId);
        var imageIds = tracker.getImageEmbeddingIds(batchId);

        if (textIds.isEmpty() && imageIds.isEmpty()) {
            throw new ResourceNotFoundException("Batch non trouvé : " + batchId);
        }

        return StatusResponse.builder()
            .found(true)
            .batchId(batchId)
            .textEmbeddings(textIds.size())
            .imageEmbeddings(imageIds.size())
            .totalEmbeddings(textIds.size() + imageIds.size())
            .message("Batch trouvé")
            .build();
    }

    @Override
    public RollbackResponse rollback(String batchId) {
        log.info("🔄 Rollback : {}", batchId);
        int deleted = tracker.rollbackBatch(batchId);
        log.info("✅ Rollback OK : {} — {} embeddings supprimés", batchId, deleted);

        return RollbackResponse.builder()
            .success(true)
            .batchId(batchId)
            .deletedCount(deleted)
            .message("Rollback réussi — " + deleted + " embeddings supprimés")
            .build();
    }

    // -------------------------------------------------------------------------
    // Monitoring
    // -------------------------------------------------------------------------

    @Override
    public ActiveIngestionsResponse getActiveIngestions() {
        List<IngestionStatusInfo> list = ingestionService.getActiveIngestions().stream()
            .map(s -> IngestionStatusInfo.builder()
                .batchId(s.getBatchId())
                .filename(s.getFilename())
                .strategy(s.getStrategy())
                .startTime(new Date(s.getStartTime()))
                .completed(s.isCompleted())
                .success(s.isSuccess())
                .duration(s.getDuration())
                .build())
            .toList();

        return ActiveIngestionsResponse.builder()
            .count(list.size())
            .ingestions(list)
            .build();
    }

    @Override
    public StatsResponse getStats() {
        var stats = ingestionService.getStats();
        return StatsResponse.builder()
            .strategiesCount(stats.strategiesCount())
            .activeIngestions(stats.activeIngestions())
            .trackerBatches(stats.trackerBatches())
            .trackerEmbeddings(stats.trackerEmbeddings())
            .filesInProgress(stats.filesInProgress())
            .build();
    }

    @Override
    public DetailedHealthResponse getDetailedHealth() {
        var report = ingestionService.getHealthReport();
        return DetailedHealthResponse.builder()
            .status(report.status())
            .healthy(report.isHealthy())
            .strategies(report.strategies())
            .activeIngestions(report.activeIngestions())
            .trackerBatches(report.trackerBatches())
            .redisHealthy(report.redisHealthy())
            .antivirusHealthy(report.antivirusHealthy())
            .antivirusEnabled(report.antivirusEnabled())
            .timestamp(new Date())
            .build();
    }

    @Override
    public StrategiesResponse getStrategies() {
        List<StrategyInfo>  list = ingestionService.getStrategiesInfo().stream()
            .map(i -> StrategyInfo.builder()
                .name(i.name())
                .priority(i.priority())
                .className(i.className())
                .build())
            .toList();

        return StrategiesResponse.builder()
            .count(list.size())
            .strategies(list)
            .build();
    }

    // -------------------------------------------------------------------------
    // Helpers privés
    // -------------------------------------------------------------------------

    private String resolveOrGenerateBatchId(String batchId) {
        return (batchId == null || batchId.isBlank()) ? UUID.randomUUID().toString() : batchId;
    }

    private String batchMessage(DuplicateSummary duplicates) {
        return duplicates.hasNone()
            ? "Batch en cours"
            : String.format("Batch en cours (%d doublon(s) seront ignorés)", duplicates.getCount());
    }

    private long totalSize(List<MultipartFile> files) {
        return files.stream().mapToLong(MultipartFile::getSize).sum();
    }

    private List<String> fileNames(List<MultipartFile> files) {
        return files.stream().map(MultipartFile::getOriginalFilename).toList();
    }

    private AsyncResponse buildDuplicateAsyncResponse(MultipartFile file, String existingBatchId) {
        log.warn("⚠️ Async — doublon détecté avant traitement : {} (batch : {})",
            file.getOriginalFilename(), existingBatchId);
        return AsyncResponse.builder()
            .accepted(false)
            .batchId(existingBatchId)
            .filename(file.getOriginalFilename())
            .message("Ce fichier a déjà été uploadé")
            .statusUrl(STATUS_URL_TEMPLATE + existingBatchId)
            .duplicate(true)
            .existingBatchId(existingBatchId)
            .build();
    }

    // -------------------------------------------------------------------------
    // Async launchers — isole les CompletableFuture du flux principal
    // -------------------------------------------------------------------------

    private void launchAsync(MultipartFile file, String batchId) {
        ingestionService.ingestFileAsync(file, batchId)
            .thenAccept(r -> log.info("✅ Async OK : {} — text={} images={}",
                file.getOriginalFilename(), r.textEmbeddings(), r.imageEmbeddings()))
            .exceptionally(ex -> handleAsyncError(ex, file.getOriginalFilename(), batchId));
    }

    private void launchBatchAsync(List<MultipartFile> files, String batchId, int duplicateCount) {
        ingestionService.ingestBatch(files, batchId)
            .thenAccept(r -> log.info("✅ Batch OK : {}/{} traités ({} doublons ignorés)",
                r.size(), files.size(), duplicateCount))
            .exceptionally(ex -> handleAsyncError(ex, "batch", batchId));
    }

    private void launchBatchDetailedAsync(List<MultipartFile> files, String batchId, int duplicateCount) {
        ingestionService.ingestBatchDetailed(files, batchId)
            .thenAccept(r -> log.info("✅ Batch détaillé OK : {}/{} succès, {} doublons, durée={}ms",
                r.successCount(), files.size(), r.duplicateCount(), r.durationMs()))
            .exceptionally(ex -> handleAsyncError(ex, "batch-detailed", batchId));
    }

    private Void handleAsyncError(Throwable ex, String filename, String batchId) {
        if (ex.getCause() instanceof DuplicateFileException dupEx) {
            log.warn("⚠️ Async doublon pendant traitement : {} (batch : {})",
                filename, dupEx.getExistingBatchId());
            notifyProgress(batchId, filename,
                "Fichier déjà uploadé (batch : " + dupEx.getExistingBatchId() + ")");
        } else {
            log.error("❌ Async erreur : {}", filename, ex);
            notifyProgress(batchId, filename, "Erreur : " + ex.getMessage());
        }
        return null;
    }

    private void notifyProgress(String batchId, String filename, String errorMessage) {
        try {
            progressService.error(batchId, filename, errorMessage);
        } catch (Exception e) {
            log.debug("Impossible d'envoyer la notification WebSocket", e);
        }
    }

        // -------------------------------------------------------------------------
    // Gestion des checked exceptions
    // -------------------------------------------------------------------------
 
    /**
     * Exécute un appel de service qui déclare {@code throws Exception}
     * et encapsule toute checked exception dans une {@link RuntimeException}.
     *
     * Clean code : évite de polluer les signatures des méthodes de la facade
     * avec {@code throws Exception}, qui forcerait les controllers à gérer
     * des checked exceptions non métier.
     *
     * @param supplier logique à exécuter
     * @param <T>      type de retour
     * @return résultat du supplier
     */
    private <T> T executeIngestion(CheckedSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (DuplicateFileException e) {
            // Laisse remonter telle quelle — gérée par IngestionExceptionHandler
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Erreur lors de l'ingestion : " + e.getMessage(), e);
        }
    }
 
    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}