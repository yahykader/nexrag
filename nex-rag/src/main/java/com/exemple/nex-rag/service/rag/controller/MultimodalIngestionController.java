package com.exemple.nexrag.service.rag.controller;

import com.exemple.nexrag.dto.*;
import com.exemple.nexrag.service.rag.facade.IngestionFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Controller REST pour l'ingestion multimodale de documents.
 *
 * Principe SRP : unique responsabilité → router les requêtes HTTP.
 *                Zéro logique métier — tout délégué à {@link IngestionFacade}.
 * Principe DIP : dépend de l'abstraction IngestionFacade, pas des services concrets.
 * Clean code   : zéro try/catch, zéro magic number, zéro DTO inline.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ingestion")
@RequiredArgsConstructor
@Tag(name = "Ingestion", description = "API d'ingestion multimodale avec monitoring avancé")
public class MultimodalIngestionController {

    private final IngestionFacade ingestionFacade;

    // =========================================================================
    // UPLOAD SYNCHRONE
    // =========================================================================

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary     = "Upload fichier (synchrone)",
        description = "Streaming automatique >100 MB, détection doublons automatique"
    )
    public ResponseEntity<IngestionResponse> uploadFile(
            @Parameter(description = "Fichier à ingérer")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "ID batch (optionnel)")
            @RequestParam(value = "batchId", required = false) String batchId) {

        return ResponseEntity.ok(ingestionFacade.uploadSync(file, batchId));
    }

    // =========================================================================
    // UPLOAD ASYNCHRONE
    // =========================================================================

    @PostMapping(value = "/upload/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary     = "Upload asynchrone",
        description = "Retourne immédiatement avec batchId, détection doublons avant traitement"
    )
    public ResponseEntity<AsyncResponse> uploadFileAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "batchId", required = false) String batchId) {

        AsyncResponse response = ingestionFacade.uploadAsync(file, batchId);
        HttpStatus    status   = response.getDuplicate() ? HttpStatus.CONFLICT : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    // =========================================================================
    // UPLOAD BATCH
    // =========================================================================

    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary     = "Upload multiple fichiers",
        description = "Traitement batch asynchrone avec détection doublons pré-traitement"
    )
    public ResponseEntity<BatchResponse> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "batchId", required = false) String batchId) {

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(ingestionFacade.uploadBatch(files, batchId));
    }

    @PostMapping(value = "/upload/batch/detailed", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary     = "Upload batch avec détails par fichier",
        description = "Retourne le résultat détaillé pour chaque fichier du batch"
    )
    public ResponseEntity<BatchDetailedResponse> uploadBatchDetailed(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "batchId", required = false) String batchId) {

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(ingestionFacade.uploadBatchDetailed(files, batchId));
    }

    // =========================================================================
    // SUIVI
    // =========================================================================

    @GetMapping("/status/{batchId}")
    @Operation(summary = "Statut d'une ingestion")
    public ResponseEntity<StatusResponse> getStatus(@PathVariable String batchId) {
        return ResponseEntity.ok(ingestionFacade.getStatus(batchId));
    }

    @DeleteMapping("/rollback/{batchId}")
    @Operation(summary = "Rollback d'une ingestion")
    public ResponseEntity<RollbackResponse> rollback(@PathVariable String batchId) {
        return ResponseEntity.ok(ingestionFacade.rollback(batchId));
    }

    // =========================================================================
    // MONITORING
    // =========================================================================

    @GetMapping("/active")
    @Operation(summary = "Liste des ingestions en cours")
    public ResponseEntity<ActiveIngestionsResponse> getActiveIngestions() {
        return ResponseEntity.ok(ingestionFacade.getActiveIngestions());
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques globales")
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(ingestionFacade.getStats());
    }

    @GetMapping("/health/detailed")
    @Operation(summary = "Health check détaillé")
    public ResponseEntity<DetailedHealthResponse> healthDetailed() {
        DetailedHealthResponse response = ingestionFacade.getDetailedHealth();
        HttpStatus status = Boolean.TRUE.equals(response.getHealthy())
            ? HttpStatus.OK
            : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/strategies")
    @Operation(summary = "Liste des stratégies disponibles")
    public ResponseEntity<StrategiesResponse> getStrategies() {
        return ResponseEntity.ok(ingestionFacade.getStrategies());
    }

    @GetMapping("/health")
    @Operation(summary = "Health check basique")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status",              "UP",
            "service",             "ingestion",
            "timestamp",           new Date(),
            "streaming",           true,
            "maxFileSize",         "5GB",
            "duplicateDetection",  true,
            "websocketProgress",   true
        ));
    }
}