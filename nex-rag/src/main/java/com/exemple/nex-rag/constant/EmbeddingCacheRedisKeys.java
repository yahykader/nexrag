package com.exemple.nexrag.constant;

/**
 * Constantes des clés Redis pour le cache d'embeddings.
 *
 * Clean code : élimine les magic strings {@code "emb:"} et {@code "batch:emb:"}
 *              éparpillées dans le service.
 * Principe OCP : ajouter un nouveau type de clé = ajouter une constante ici.
 */
public final class EmbeddingCacheRedisKeys {

    /** Préfixe des clés d'embeddings. */
    public static final String EMB_PREFIX   = "emb:";

    /** Préfixe des clés de tracking par batch. */
    public static final String BATCH_PREFIX = "batch:emb:";

    private EmbeddingCacheRedisKeys() {}

    /** Clé Redis pour l'embedding d'un hash de texte. */
    public static String forHash(String textHash) {
        return EMB_PREFIX + textHash;
    }

    /** Clé Redis pour le set de tracking d'un batch. */
    public static String forBatch(String batchId) {
        return BATCH_PREFIX + batchId;
    }
}