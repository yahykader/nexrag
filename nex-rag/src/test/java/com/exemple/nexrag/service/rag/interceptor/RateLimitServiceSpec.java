package com.exemple.nexrag.service.rag.interceptor;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec unitaire de {@link RateLimitService}.
 *
 * SRP : valide uniquement la logique de quota Bucket4j —
 *       pas de vérification du comportement HTTP (responsabilité de l'intercepteur).
 * DIP : {@link ProxyManager} et {@link Bucket} injectés comme mocks ;
 *       aucun Redis réel n'est démarré.
 */
@DisplayName("Spec : RateLimitService — Gestion des quotas Bucket4j/Redis")
@ExtendWith(MockitoExtension.class)
class RateLimitServiceSpec {

    @Mock
    private ProxyManager<String> proxyManager;

    @SuppressWarnings("unchecked")
    @Mock
    private RemoteBucketBuilder<String> remoteBucketBuilder;

    @Mock
    private BucketProxy bucketProxy;

    @Mock
    private ConsumptionProbe probe;

    @SuppressWarnings("unchecked")
    @Mock
    private Supplier<BucketConfiguration> uploadConfig;

    @SuppressWarnings("unchecked")
    @Mock
    private Supplier<BucketConfiguration> batchConfig;

    @SuppressWarnings("unchecked")
    @Mock
    private Supplier<BucketConfiguration> deleteConfig;

    @SuppressWarnings("unchecked")
    @Mock
    private Supplier<BucketConfiguration> searchConfig;

    @SuppressWarnings("unchecked")
    @Mock
    private Supplier<BucketConfiguration> defaultConfig;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService(
            proxyManager, uploadConfig, batchConfig, deleteConfig, searchConfig, defaultConfig
        );
        when(proxyManager.builder()).thenReturn(remoteBucketBuilder);
        when(remoteBucketBuilder.build(anyString(), any(Supplier.class))).thenReturn(bucketProxy);
    }

    // -------------------------------------------------------------------------
    // AC-21.2 — Token consommé → allowed avec remainingTokens
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner allowed avec tokens restants quand le token est consommé")
    void devraitRetournerAllowedAvecTokensRestantsQuandTokenConsomme() {
        // Given
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(9L);

        // When
        RateLimitResult result = service.checkUploadLimit("user-1");

        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isEqualTo(9L);
        assertThat(result.getRetryAfterSeconds()).isZero();
    }

    // -------------------------------------------------------------------------
    // AC-21.1 — Limite atteinte → blocked avec retryAfterSeconds
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner blocked avec retryAfterSeconds quand la limite est atteinte")
    void devraitRetournerBlockedAvecRetryAfterQuandLimiteAtteinte() {
        // Given
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(30_000_000_000L); // 30 secondes

        // When
        RateLimitResult result = service.checkUploadLimit("user-1");

        // Then
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRetryAfterSeconds()).isEqualTo(30L);
        assertThat(result.getRemainingTokens()).isZero();
    }

    // -------------------------------------------------------------------------
    // AC-21.6 — Exception Redis → fail-open : allowed(0)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner allowed(0) en cas d'exception Redis (fail-open)")
    void devraitRetournerAllowedZeroEnCasExceptionRedis() {
        // Given
        when(bucketProxy.tryConsumeAndReturnRemaining(1))
            .thenThrow(new RuntimeException("Redis indisponible"));

        // When
        RateLimitResult result = service.checkUploadLimit("user-1");

        // Then — fail-open : la requête est autorisée même si Redis est en panne
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isZero();
    }

    // -------------------------------------------------------------------------
    // AC-21.5 — Clé Redis : rate-limit:{userId}:{endpoint}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT construire la clé Redis rate-limit:{userId}:{endpoint} pour l'endpoint search")
    void devraitConstruireCleRedisCorrectePourEndpointSearch() {
        // Given
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(5L);

        // When
        service.checkSearchLimit("user-99");

        // Then
        verify(remoteBucketBuilder).build(eq("rate-limit:user-99:search"), any(Supplier.class));
    }
}
