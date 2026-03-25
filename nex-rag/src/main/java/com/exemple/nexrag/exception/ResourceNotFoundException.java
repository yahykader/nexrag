package com.exemple.nexrag.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception métier : ressource introuvable (404).
 *
 * Principe SRP : unique responsabilité → représenter l'absence d'une ressource.
 * Clean code   : exception nommée plutôt qu'un boolean + message dans le controller.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}