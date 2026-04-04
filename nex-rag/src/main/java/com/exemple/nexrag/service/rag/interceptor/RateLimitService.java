package com.exemple.nexrag.service.rag.interceptor;

import com.exemple.nexrag.config.RateLimitProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Service de Rate Limiting.
 *
 * Principe SRP  : unique responsabilité → vérifier et appliquer les quotas
 *                 par utilisateur et par endpoint.
 * Principe DIP  : dépend de {@link RateLimitProperties} — pas de @Value inline.
 * Clean code    : {@link RateLimitResult} extrait dans sa propre classe.
 *                 Constructeur à 5 paramètres nommés remplace les 6 Supplier anonymes.
 *
 * @author RAG Team
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ProxyManager<String>            proxyManager;
    private final Supplier<BucketConfiguration>   uploadBucketConfig;
    private final Supplier<BucketConfiguration>   batchBucketConfig;
    private final Supplier<BucketConfiguration>   deleteBucketConfig;
    private final Supplier<BucketConfiguration>   searchBucketConfig;
    private final Supplier<BucketConfiguration>   defaultBucketConfig;

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    public RateLimitResult checkUploadLimit(String userId)  { return check(userId, "upload",  uploadBucketConfig);  }
    public RateLimitResult checkBatchLimit(String userId)   { return check(userId, "batch",   batchBucketConfig);   }
    public RateLimitResult checkDeleteLimit(String userId)  { return check(userId, "delete",  deleteBucketConfig);  }
    public RateLimitResult checkSearchLimit(String userId)  { return check(userId, "search",  searchBucketConfig);  }
    public RateLimitResult checkDefaultLimit(String userId) { return check(userId, "default", defaultBucketConfig); }

    // -------------------------------------------------------------------------
    // Logique commune
    // -------------------------------------------------------------------------

    private RateLimitResult check(String userId, String endpoint,
                                  Supplier<BucketConfiguration> config) {
        try {
            String bucketKey = "rate-limit:" + userId + ":" + endpoint;
            Bucket bucket    = proxyManager.builder().build(bucketKey, config);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (probe.isConsumed()) {
                log.debug("✅ [RateLimit] OK — user={} endpoint={} remaining={}",
                    userId, endpoint, probe.getRemainingTokens());
                return RateLimitResult.allowed(probe.getRemainingTokens());
            }

            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            log.warn("⚠️ [RateLimit] BLOQUÉ — user={} endpoint={} retry_in={}s",
                userId, endpoint, waitSeconds);
            return RateLimitResult.blocked(waitSeconds);

        } catch (Exception e) {
            // Fail-open : en cas d'erreur Redis, la requête est autorisée
            log.error("❌ [RateLimit] Erreur vérification — user={} endpoint={}",
                userId, endpoint, e);
            return RateLimitResult.allowed(0);
        }
    }
}