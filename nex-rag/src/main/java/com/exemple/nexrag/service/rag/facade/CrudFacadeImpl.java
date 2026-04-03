package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.dto.*;
import com.exemple.nexrag.exception.ResourceNotFoundException;
import com.exemple.nexrag.service.rag.ingestion.IngestionOrchestrator;
import com.exemple.nexrag.service.rag.ingestion.repository.EmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Implémentation de la facade CRUD.
 *
 * Principe SRP : unique responsabilité → orchestrer les opérations CRUD métier.
 * Principe DIP : dépend des abstractions (EmbeddingRepository, IngestionOrchestrator).
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrudFacadeImpl implements CrudFacade {

    private static final String CONFIRMATION_TOKEN = "DELETE_ALL_FILES";

    private final EmbeddingRepository   embeddingRepository;
    private final IngestionOrchestrator ingestionOrchestrator;

    // -------------------------------------------------------------------------
    // Suppressions individuelles
    // -------------------------------------------------------------------------

    @Override
    public DeleteResponse deleteById(String embeddingId, EmbeddingType type) {
        log.info("🗑️ deleteById — id={}, type={}", embeddingId, type);

        boolean deleted = switch (type) {
            case TEXT  -> embeddingRepository.deleteText(embeddingId);
            case IMAGE -> embeddingRepository.deleteImage(embeddingId);
        };

        if (!deleted) {
            throw new ResourceNotFoundException(
                "Embedding non trouvé : " + embeddingId + " (type: " + type + ")"
            );
        }

        log.info("✅ Supprimé : id={}, type={}", embeddingId, type);

        return DeleteResponse.builder()
            .success(true)
            .deletedCount(1)
            .embeddingId(embeddingId)
            .type(type.name().toLowerCase())
            .message("Fichier supprimé avec succès")
            .build();
    }

    @Override
    public DeleteResponse deleteBatch(List<String> ids, EmbeddingType type) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("La liste d'IDs ne peut pas être vide");
        }

        log.info("🗑️ deleteBatch — {} ids, type={}", ids.size(), type);

        int deleted = switch (type) {
            case TEXT  -> embeddingRepository.deleteTextBatch(ids);
            case IMAGE -> embeddingRepository.deleteImageBatch(ids);
        };

        log.info("✅ Batch supprimé : {}/{} (type={})", deleted, ids.size(), type);

        return DeleteResponse.builder()
            .success(true)
            .deletedCount(deleted)
            .type(type.name().toLowerCase())
            .message(String.format("%d/%d embeddings %s supprimés", deleted, ids.size(), type))
            .build();
    }

    // -------------------------------------------------------------------------
    // Suppressions par batch métier
    // -------------------------------------------------------------------------

    @Override
    public DeleteResponse deleteBatchById(String batchId) {
        log.info("🗑️ deleteBatchById — batchId={}", batchId);

        if (!embeddingRepository.batchExists(batchId)) {
            throw new ResourceNotFoundException("Batch non trouvé : " + batchId);
        }

        Map<String, Integer> stats   = embeddingRepository.getBatchStats(batchId);
        int                  deleted = embeddingRepository.deleteBatch(batchId);

        String message = String.format(
            "Batch supprimé : %d embeddings (text: %d, images: %d) + caches Redis",
            deleted,
            stats.get("textEmbeddings"),
            stats.get("imageEmbeddings")
        );

        log.info("✅ {}", message);

        return DeleteResponse.builder()
            .success(true)
            .deletedCount(deleted)
            .batchId(batchId)
            .message(message)
            .build();
    }

    @Override
    public DeleteResponse deleteAll(String confirmation) {
        if (!CONFIRMATION_TOKEN.equals(confirmation)) {
            throw new IllegalArgumentException(
                "Confirmation requise : confirmation=" + CONFIRMATION_TOKEN
            );
        }

        log.warn("🚨 deleteAll — SUPPRESSION GLOBALE DEMANDÉE");

        int deleted = embeddingRepository.deleteAllFilesPlusCache();
        embeddingRepository.clearAllTracking();

        String message = String.format(
            "TOUS les fichiers supprimés : %d embeddings + caches Redis + tracker",
            deleted
        );

        log.warn("✅ {}", message);

        return DeleteResponse.builder()
            .success(true)
            .deletedCount(deleted)
            .message(message)
            .timestamp(new Date())
            .build();
    }

    // -------------------------------------------------------------------------
    // Lecture / Vérification
    // -------------------------------------------------------------------------

    @Override
    public DuplicateCheckResponse checkDuplicate(MultipartFile file) {
        log.info("🔍 checkDuplicate — {}", file.getOriginalFilename());

        boolean exists = ingestionOrchestrator.fileExists(file);

        if (exists) {
            String batchId = ingestionOrchestrator.getExistingBatchId(file);
            log.info("⚠️ Doublon détecté — file={}, batchId={}", file.getOriginalFilename(), batchId);

            return DuplicateCheckResponse.builder()
                .duplicate(true)
                .filename(file.getOriginalFilename())
                .existingBatchId(batchId)
                .message("Ce fichier existe déjà dans le système")
                .build();
        }

        return DuplicateCheckResponse.builder()
            .duplicate(false)
            .filename(file.getOriginalFilename())
            .message("Fichier non trouvé — peut être uploadé")
            .build();
    }

    @Override
    public BatchInfoResponse getBatchInfo(String batchId) {
        log.info("📊 getBatchInfo — batchId={}", batchId);

        if (!embeddingRepository.batchExists(batchId)) {
            throw new ResourceNotFoundException("Batch non trouvé : " + batchId);
        }

        Map<String, Integer> stats = embeddingRepository.getBatchStats(batchId);
        int textCount  = stats.get("textEmbeddings");
        int imageCount = stats.get("imageEmbeddings");

        return BatchInfoResponse.builder()
            .found(true)
            .batchId(batchId)
            .textEmbeddings(textCount)
            .imageEmbeddings(imageCount)
            .totalEmbeddings(textCount + imageCount)
            .message("Batch trouvé")
            .build();
    }

    @Override
    public SystemStatsResponse getSystemStats() {
        log.info("📊 getSystemStats");

        var statsResponse  = ingestionOrchestrator.getStats();
        var detailedHealthResponse  = ingestionOrchestrator.getHealthReport();

        return SystemStatsResponse.builder()
            .totalStrategies(statsResponse.strategiesCount())
            .activeIngestions(statsResponse.activeIngestions())
            .trackedBatches(statsResponse.trackerBatches())
            .totalEmbeddings(statsResponse.trackerEmbeddings())
            .filesInProgress(statsResponse.filesInProgress())
            .redisHealthy(detailedHealthResponse.redisHealthy())
            .systemStatus(detailedHealthResponse.status())
            .build();
    }
}