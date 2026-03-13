package com.exemple.nexrag.advice;

import com.exemple.nexrag.dto.TranscriptionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Gestionnaire global des exceptions du controller Voice.
 *
 * Principe SRP : unique responsabilité → traduire les exceptions en réponses HTTP.
 * Clean code   : supprime le try/catch inline dans le controller.
 */
@Slf4j
@RestControllerAdvice
public class VoiceExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<TranscriptionResponse> handleValidation(IllegalArgumentException e) {
        log.warn("⚠️ [Voice] Validation échouée : {}", e.getMessage());
        return ResponseEntity
            .badRequest()
            .body(errorResponse(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TranscriptionResponse> handleGeneric(Exception e) {
        log.error("❌ [Voice] Erreur transcription", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorResponse("Erreur lors de la transcription : " + e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Utilitaire
    // -------------------------------------------------------------------------

    private TranscriptionResponse errorResponse(String message) {
        return TranscriptionResponse.builder()
            .success(false)
            .transcript(message)
            .build();
    }
}