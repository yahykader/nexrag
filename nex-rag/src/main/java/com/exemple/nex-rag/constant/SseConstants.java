package com.exemple.nexrag.constant;

/**
 * Constantes de configuration SSE.
 *
 * Clean code : élimine le magic number 300000 dans le controller.
 */
public final class SseConstants {

    /** Timeout SSE : 5 minutes en millisecondes. */
    public static final long TIMEOUT_MS = 5 * 60 * 1000L;

    /** Préfixe des identifiants de session. */
    public static final String SESSION_PREFIX = "session_";

    /** Longueur de la partie aléatoire du sessionId. */
    public static final int SESSION_ID_RANDOM_LENGTH = 16;

    /** Longueur max des logs de requête tronquée. */
    public static final int LOG_QUERY_MAX_LENGTH = 50;

    private SseConstants() {}
}