package com.exemple.nexrag.constant;

/**
 * Constantes des clés Redis pour la déduplication de textes.
 *
 * Clean code : élimine les @Value redisPrefix éparpillés et la magic string "1".
 * Principe OCP : ajouter un pattern = ajouter une constante ici.
 */
public final class TextDeduplicationRedisKeys {

    /** Préfixe des clés de déduplication de texte. */
    public static final String DEDUP_PREFIX   = "text:dedup:";

    /** Préfixe des clés de tracking par batch. */
    public static final String BATCH_PREFIX   = "batch:text:";

    /** Valeur stockée dans Redis pour marquer un texte comme indexé. */
    public static final String INDEXED_VALUE  = "1";

    private TextDeduplicationRedisKeys() {}

    /** Clé Redis pour un hash de texte. */
    public static String forHash(String hash) {
        return DEDUP_PREFIX + hash;
    }

    /** Clé Redis pour le set de tracking d'un batch. */
    public static String forBatch(String batchId) {
        return BATCH_PREFIX + batchId;
    }

    /** Clé Redis scoped par batch (si batchIdScope activé). */
    public static String forHashInBatch(String batchId, String hash) {
        return DEDUP_PREFIX + batchId + ":" + hash;
    }
}