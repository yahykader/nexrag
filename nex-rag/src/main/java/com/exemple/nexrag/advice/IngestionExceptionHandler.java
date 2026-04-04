package com.exemple.nexrag.advice;

import com.exemple.nexrag.exception.AntivirusScanException;
import com.exemple.nexrag.service.rag.controller.MultimodalIngestionController;
import com.exemple.nexrag.dto.ErrorResponse;
import com.exemple.nexrag.dto.IngestionResponse;
import com.exemple.nexrag.exception.DuplicateFileException;
import com.exemple.nexrag.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Gestionnaire global des exceptions du controller d'ingestion.
 *
 * Principe SRP : unique responsabilité → traduire les exceptions en réponses HTTP.
 * Clean code   : supprime les 6 blocs try/catch dupliqués dans le controller.
 *                Supprime le doublon handleDuplicate — une seule méthode par exception.
 *                Supprime @ResponseStatus redondant — ResponseEntity prime toujours.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = MultimodalIngestionController.class)
public class IngestionExceptionHandler {

    @ExceptionHandler(DuplicateFileException.class)
    public ResponseEntity<IngestionResponse> handleDuplicate(DuplicateFileException e) {
        log.warn("⚠️ Doublon détecté : {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(IngestionResponse.builder()
                .success(false)
                .duplicate(true)
                .existingBatchId(e.getExistingBatchId())
                .message("Ce fichier a déjà été uploadé et traité")
                .build());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("⚠️ Paramètre multipart manquant : {}", e.getRequestPartName());
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("Paramètre manquant : " + e.getRequestPartName()));
    }

    @ExceptionHandler(AntivirusScanException.class)
    public ResponseEntity<IngestionResponse> handleAntivirusScan(AntivirusScanException e) {
        log.error("⚠️ Erreur scan antivirus : {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(IngestionResponse.builder()
                .success(false)
                .message("Le service antivirus est temporairement indisponible. Réessayez dans quelques instants.")
                .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<IngestionResponse> handleValidation(IllegalArgumentException e) {
        log.warn("⚠️ Validation échouée : {}", e.getMessage());
        return ResponseEntity
            .badRequest()
            .body(errorResponse(e.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<IngestionResponse> handleNotFound(ResourceNotFoundException e) {
        log.warn("🔍 Ressource non trouvée : {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(errorResponse(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<IngestionResponse> handleGeneric(Exception e) {
        log.error("❌ Erreur interne inattendue", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorResponse("Erreur interne : " + e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Utilitaire
    // -------------------------------------------------------------------------

    private IngestionResponse errorResponse(String message) {
        return IngestionResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
}