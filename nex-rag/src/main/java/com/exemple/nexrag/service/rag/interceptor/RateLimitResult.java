package com.exemple.nexrag.service.rag.interceptor;

/**
 * Résultat d'une vérification de rate limit.
 *
 * Principe SRP  : extrait hors de {@link RateLimitService} — une classe
 *                 par responsabilité.
 * Clean code    : fabriques statiques expressives {@code allowed()} / {@code blocked()}.
 */
public class RateLimitResult {

    private final boolean allowed;
    private final long    remainingTokens;
    private final long    retryAfterSeconds;

    private RateLimitResult(boolean allowed, long remainingTokens, long retryAfterSeconds) {
        this.allowed           = allowed;
        this.remainingTokens   = remainingTokens;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static RateLimitResult allowed(long remainingTokens) {
        return new RateLimitResult(true, remainingTokens, 0);
    }

    public static RateLimitResult blocked(long retryAfterSeconds) {
        return new RateLimitResult(false, 0, retryAfterSeconds);
    }

    public boolean isAllowed()            { return allowed;           }
    public long    getRemainingTokens()   { return remainingTokens;   }
    public long    getRetryAfterSeconds() { return retryAfterSeconds; }
}