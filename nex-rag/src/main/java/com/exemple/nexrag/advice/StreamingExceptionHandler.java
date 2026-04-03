package com.exemple.nexrag.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.exemple.nexrag.service.rag.controller.StreamingAssistantController;

import java.util.Map;

@Slf4j
@RestControllerAdvice(assignableTypes = StreamingAssistantController.class)
public class StreamingExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null
            ? e.getBindingResult().getFieldError().getDefaultMessage()
            : "Validation échouée";
        log.warn("⚠️ [Stream] Validation : {}", message);
        return ResponseEntity.badRequest()
            .body(Map.of("error", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleValidation(IllegalArgumentException e) {
        log.warn("⚠️ [Stream] Argument invalide : {}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception e) {
        log.error("❌ [Stream] Erreur inattendue", e);
        return ResponseEntity.internalServerError()
            .body(Map.of("error", "Erreur interne : " + e.getMessage()));
    }
}