package com.exemple.nexrag.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Configuration du Rate Limiting avec Bucket4j + Redis.
 *
 * Principe SRP  : chaque méthode configure un seul bean.
 * Principe DIP  : dépend de {@link RateLimitProperties} pour les quotas.
 *                 Redis lu depuis {@code spring.redis.*} — pas de duplication
 *                 de la configuration Redis déjà présente dans
 *                 {@code application.yml}.
 * Clean code    : {@code redisConnection()} et {@code bucketConfig()} extraits
 *                 — chaque méthode privée fait une seule chose.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RateLimitConfig {

    private final RateLimitProperties props;

    // Redis — réutilise spring.redis.* défini dans application.yml
    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    // -------------------------------------------------------------------------
    // ProxyManager Redis
    // -------------------------------------------------------------------------

    @Bean
    public ProxyManager<String> proxyManager() {
        StatefulRedisConnection<String, byte[]> connection = redisConnection();

        ProxyManager<String> manager = LettuceBasedProxyManager.builderFor(connection)
            .withExpirationStrategy(
                ExpirationAfterWriteStrategy
                    .basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(1))
            )
            .build();

        log.info("✅ Rate Limiting activé (Redis: {}:{})", redisHost, redisPort);
        log.info("📊 Limites — upload={} batch={} delete={} search={} default={}",
            props.getUpload().getRequestsPerMinute(),
            props.getBatch().getRequestsPerMinute(),
            props.getDelete().getRequestsPerMinute(),
            props.getSearch().getRequestsPerMinute(),
            props.getDefaultEndpoint().getRequestsPerMinute());

        return manager;
    }

    // -------------------------------------------------------------------------
    // Configurations des buckets par endpoint
    // -------------------------------------------------------------------------

    @Bean public Supplier<BucketConfiguration> uploadBucketConfig() {
        return bucketConfig(props.getUpload().getRequestsPerMinute());
    }

    @Bean public Supplier<BucketConfiguration> batchBucketConfig() {
        return bucketConfig(props.getBatch().getRequestsPerMinute());
    }

    @Bean public Supplier<BucketConfiguration> deleteBucketConfig() {
        return bucketConfig(props.getDelete().getRequestsPerMinute());
    }

    @Bean public Supplier<BucketConfiguration> searchBucketConfig() {
        return bucketConfig(props.getSearch().getRequestsPerMinute());
    }

    @Bean public Supplier<BucketConfiguration> defaultBucketConfig() {
        return bucketConfig(props.getDefaultEndpoint().getRequestsPerMinute());
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private StatefulRedisConnection<String, byte[]> redisConnection() {
        String uri = redisPassword != null && !redisPassword.isBlank()
            ? "redis://:%s@%s:%d".formatted(redisPassword, redisHost, redisPort)
            : "redis://%s:%d".formatted(redisHost, redisPort);

        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return RedisClient.create(uri).connect(codec);
    }

    private static Supplier<BucketConfiguration> bucketConfig(int requestsPerMinute) {
        return () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.simple(requestsPerMinute, Duration.ofMinutes(1)))
            .build();
    }
}