package com.exemple.nexrag.exception;

/**
 * Exception levée lors d'un problème technique de scan antivirus.
 *
 * Clean code : étend {@link RuntimeException} au lieu de {@link Exception} —
 *              les exceptions techniques (timeout, connexion refusée) sont
 *              non récupérables et ne doivent pas être déclarées dans les
 *              signatures de méthode. Propagation correcte dans les contextes
 *              {@code @Async} sans être wrappée dans {@link java.util.concurrent.CompletionException}.
 */
public class AntivirusScanException extends RuntimeException {

    public AntivirusScanException(String message) {
        super(message);
    }

    public AntivirusScanException(String message, Throwable cause) {
        super(message, cause);
    }
}