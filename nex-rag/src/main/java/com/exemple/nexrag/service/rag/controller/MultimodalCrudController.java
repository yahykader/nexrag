package com.exemple.nexrag.service.rag.controller;

import com.exemple.nexrag.dto.*;
import com.exemple.nexrag.service.rag.facade.CrudFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controller CRUD pour la gestion des embeddings.
 *
 * Principe SRP : unique responsabilité → router les requêtes HTTP vers la facade.
 *                Aucune logique métier ici — tout délégué à {@link CrudFacade}.
 * Principe DIP : dépend de l'abstraction CrudFacade, pas des services concrets.
 * Clean code   : zéro try/catch, zéro logique métier, zéro DTO inline.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/crud")
@RequiredArgsConstructor
@Tag(name = "CRUD Embeddings", description = "API CRUD pour gestion des embeddings")
public class MultimodalCrudController {

    private final CrudFacade crudFacade;

    // =========================================================================
    // SUPPRESSION INDIVIDUELLE
    // =========================================================================

    @DeleteMapping("/file/{embeddingId}")
    @Operation(
        summary     = "Supprimer un fichier par ID",
        description = "Supprime un embedding texte ou image spécifique"
    )
    public ResponseEntity<DeleteResponse> deleteFile(
            @Parameter(description = "ID de l'embedding à supprimer")
            @PathVariable String embeddingId,
            @Parameter(description = "Type : 'text' ou 'image'")
            @RequestParam(defaultValue = "text") String type) {

        DeleteResponse response = crudFacade.deleteById(embeddingId, EmbeddingType.fromString(type));
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // SUPPRESSIONS BATCH
    // =========================================================================

    @DeleteMapping("/batch/{batchId}/files")
    @Operation(
        summary     = "Supprimer tous les fichiers d'un batch",
        description = "Supprime tous les embeddings (texte + images) d'un batch + cache Redis"
    )
    public ResponseEntity<DeleteResponse> deleteBatchFiles(@PathVariable String batchId) {
        DeleteResponse response = crudFacade.deleteBatchById(batchId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/files/text/batch")
    @Operation(
        summary     = "Supprimer plusieurs fichiers texte",
        description = "Supprime une liste d'embeddings texte en batch"
    )
    public ResponseEntity<DeleteResponse> deleteTextBatch(
            @Parameter(description = "Liste des IDs à supprimer")
            @RequestBody List<String> embeddingIds) {

        DeleteResponse response = crudFacade.deleteBatch(embeddingIds, EmbeddingType.TEXT);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/files/image/batch")
    @Operation(
        summary     = "Supprimer plusieurs fichiers image",
        description = "Supprime une liste d'embeddings image en batch"
    )
    public ResponseEntity<DeleteResponse> deleteImageBatch(
            @Parameter(description = "Liste des IDs à supprimer")
            @RequestBody List<String> embeddingIds) {

        DeleteResponse response = crudFacade.deleteBatch(embeddingIds, EmbeddingType.IMAGE);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // SUPPRESSION GLOBALE
    // =========================================================================

    @DeleteMapping("/files/all")
    @Operation(
        summary     = "Supprimer TOUS les fichiers",
        description = "⚠️ DANGER : supprime TOUS les embeddings + cache Redis + tracker. " +
                      "Nécessite confirmation='DELETE_ALL_FILES'"
    )
    public ResponseEntity<DeleteResponse> deleteAllFiles(
            @Parameter(description = "Confirmation requise : DELETE_ALL_FILES", required = true)
            @RequestParam String confirmation) {

        DeleteResponse response = crudFacade.deleteAll(confirmation);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // LECTURE / VÉRIFICATION
    // =========================================================================

    @PostMapping(value = "/check-duplicate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary     = "Vérifier si un fichier existe déjà",
        description = "Vérifie si le fichier a déjà été uploadé sans l'ingérer"
    )
    public ResponseEntity<DuplicateCheckResponse> checkDuplicate(
            @Parameter(description = "Fichier à vérifier")
            @RequestParam("file") MultipartFile file) {

        DuplicateCheckResponse response = crudFacade.checkDuplicate(file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/batch/{batchId}/info")
    @Operation(
        summary     = "Informations sur un batch",
        description = "Récupère les détails d'un batch spécifique"
    )
    public ResponseEntity<BatchInfoResponse> getBatchInfo(@PathVariable String batchId) {
        BatchInfoResponse response = crudFacade.getBatchInfo(batchId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/system")
    @Operation(
        summary     = "Statistiques globales du système",
        description = "Récupère les stats complètes sur l'ingestion et les doublons"
    )
    public ResponseEntity<SystemStatsResponse> getSystemStats() {
        SystemStatsResponse response = crudFacade.getSystemStats();
        return ResponseEntity.ok(response);
    }
}