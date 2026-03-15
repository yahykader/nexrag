package com.exemple.nexrag.dto.deduplication.file;

import com.exemple.nexrag.constant.DeduplicationRedisKeys;

/**
 * Statistiques du service de déduplication.
 */
public record DeduplicationStats(
    long    trackedFiles,
    boolean redisAvailable
) {
    public boolean isHealthy()    { return redisAvailable; }
    public String  keyPrefix()    { return DeduplicationRedisKeys.HASH_PREFIX; }
    public int     defaultTtlDays(){ return DeduplicationRedisKeys.DEFAULT_TTL_DAYS; }
}