package com.exemple.nexrag.constant;

/**
 * Constantes des clés Redis utilisées par la déduplication.
 *
 * Clean code : élimine {@code REDIS_KEY_PREFIX = FILE_HASH_PREFIX} (même constante définie deux fois)
 *              et les patterns hardcodés dans {@code clearAll()}.
 * Principe OCP : ajouter un nouveau pattern = ajouter une constante ici,
 *                sans modifier les services.
 */
public final class DeduplicationRedisKeys {

    /** Préfixe des clés de hash de fichiers. Valeur : {@code ingestion:hash:} */
    public static final String HASH_PREFIX = "ingestion:hash:";

    /** Pattern de suppression globale des hashs de fichiers. */
    public static final String HASH_PATTERN = HASH_PREFIX + "*";

    /** TTL par défaut pour un hash enregistré : 30 jours. */
    public static final int DEFAULT_TTL_DAYS = 30;

    private DeduplicationRedisKeys() {}

    /**
     * Construit la clé Redis complète pour un hash donné.
     *
     * @param hash hash SHA-256 encodé Base64
     * @return clé Redis complète
     */
    public static String forHash(String hash) {
        return HASH_PREFIX + hash;
    }
}