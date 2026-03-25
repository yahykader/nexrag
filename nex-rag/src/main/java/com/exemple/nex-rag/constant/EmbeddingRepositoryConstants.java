package com.exemple.nexrag.constant;

/**
 * Constantes du repository d'embeddings.
 *
 * Clean code : élimine les magic numbers 100 et 500 éparpillés dans le code.
 */
public final class EmbeddingRepositoryConstants {

    /** Taille des micro-batches lors de suppressions massives. */
    public static final int DELETE_BATCH_SIZE = 100;

    /** Fréquence de log de progression (tous les N éléments supprimés). */
    public static final int PROGRESS_LOG_INTERVAL = 500;

    /** Préfixe Redis des rate-limits. */
    public static final String RATE_LIMIT_KEY_PATTERN = "rate-limit:*";

    private EmbeddingRepositoryConstants() {}
}