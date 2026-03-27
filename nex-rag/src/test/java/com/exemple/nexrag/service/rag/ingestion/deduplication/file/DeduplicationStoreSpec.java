package com.exemple.nexrag.service.rag.ingestion.deduplication.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : DeduplicationStore — Accès Redis pour déduplication fichiers")
class DeduplicationStoreSpec {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private DeduplicationStore store;

    @Test
    @DisplayName("DOIT retourner true quand le hash existe dans Redis")
    void devraitRetournerTrueQuandHashExisteRedis() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.TRUE);
        assertThat(store.exists("unHash")).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false quand le hash est absent de Redis")
    void devraitRetournerFalseQuandHashAbsentRedis() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.FALSE);
        assertThat(store.exists("hashInexistant")).isFalse();
    }

    @Test
    @DisplayName("DOIT retourner false quand hasKey retourne null (clé expirée)")
    void devraitRetournerFalseQuandHasKeyRetourneNull() {
        when(redisTemplate.hasKey(anyString())).thenReturn(null);
        assertThat(store.exists("hashNull")).isFalse();
    }

    @Test
    @DisplayName("DOIT sauvegarder le hash avec batchId et TTL dans Redis")
    void devraitSauvegarderHashAvecBatchIdEtTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        store.save("monHash", "batch-001", 7L, TimeUnit.DAYS);

        verify(valueOps).set(anyString(), eq("batch-001"), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    @DisplayName("DOIT retourner le batchId associé au hash")
    void devraitRetournerBatchIdPourHash() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("batch-007");

        assertThat(store.getBatchId("monHash")).isEqualTo("batch-007");
    }

    @Test
    @DisplayName("DOIT supprimer le hash et retourner true si la suppression réussit")
    void devraitSupprimerHashEtRetournerTrue() {
        when(redisTemplate.delete(anyString())).thenReturn(Boolean.TRUE);
        assertThat(store.delete("hashASupprimer")).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false pour delete() si la clé n'existait pas")
    void devraitRetournerFalsePourDeleteSiCleInexistante() {
        when(redisTemplate.delete(anyString())).thenReturn(Boolean.FALSE);
        assertThat(store.delete("inexistant")).isFalse();
    }

    @Test
    @DisplayName("DOIT retourner true pour isAvailable() quand Redis répond")
    void devraitRetournerTruePourIsAvailableQuandRedisRepond() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("ping")).thenReturn(null);
        assertThat(store.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false pour isAvailable() quand Redis est inaccessible")
    void devraitRetournerFalsePourIsAvailableQuandRedisInaccessible() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));
        assertThat(store.isAvailable()).isFalse();
    }
}
