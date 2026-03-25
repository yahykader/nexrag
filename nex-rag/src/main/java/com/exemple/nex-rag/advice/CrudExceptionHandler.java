package com.exemple.nexrag.advice;

import com.exemple.nexrag.dto.DeleteResponse;
import com.exemple.nexrag.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Gestionnaire global d'exceptions pour les controllers CRUD.
 *
 * Principe SRP : unique responsabilité → centraliser la gestion des erreurs HTTP.
 * Clean code   : élimine les 8 blocs try/catch dupliqués dans le controller.
 */
@Slf4j
@RestControllerAdvice
public class CrudExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<DeleteResponse> handleNotFound(ResourceNotFoundException e) {
        log.warn("🔍 Ressource non trouvée : {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(errorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<DeleteResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("⚠️ Requête invalide : {}", e.getMessage());
        return ResponseEntity
            .badRequest()
            .body(errorResponse(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<DeleteResponse> handleGenericException(Exception e) {
        log.error("❌ Erreur interne inattendue", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorResponse("Erreur interne : " + e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Utilitaire
    // -------------------------------------------------------------------------

    private DeleteResponse errorResponse(String message) {
        return DeleteResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
}