package com.exemple.nexrag.dto.deduplication.file;

import com.exemple.nexrag.constant.DeduplicationRedisKeys;

/**
 * Informations détaillées sur un fichier tracké.
 */
public record FileInfo(
    String hash,
    String batchId,
    long   ttlSeconds
) {
    private static final long EXPIRING_SOON_THRESHOLD_SECONDS = 86_400L; // 24h

    /** Retourne les 16 premiers caractères du hash pour les logs. */
    public String shortHash() {
        return hash != null && hash.length() >= 16
            ? hash.substring(0, 16) + "..."
            : hash;
    }

    /** {@code true} si le fichier expire dans moins de 24h. */
    public boolean isExpiringSoon() {
        return ttlSeconds > 0 && ttlSeconds < EXPIRING_SOON_THRESHOLD_SECONDS;
    }
}