package com.exemple.nexrag.exception;

/**
 * Exception métier levée lors d'une erreur d'ingestion.
 *
 * Principe SRP : représente uniquement les erreurs métier d'ingestion.
 * Clean code   : remplace {@code throws Exception} dans {@link IngestionStrategy}
 *                par un type explicite et déclaratif.
 */
public class IngestionException extends Exception {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}