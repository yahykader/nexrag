package com.exemple.nexrag.service.rag.ingestion.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Spec : EmbeddingCacheStore — Accès Redis pour le cache d'embeddings.
 */
@DisplayName("Spec : EmbeddingCacheStore — CRUD Redis avec TTL pour embeddings")
@ExtendWith(MockitoExtension.class)
class EmbeddingCacheStoreSpec {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private SetOperations<String, String>   setOps;

    @InjectMocks
    private EmbeddingCacheStore cacheStore;

    // -------------------------------------------------------------------------
    // get — cache hit / miss
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner la valeur quand la clé existe dans Redis (cache hit)")
    void shouldReturnValueOnCacheHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("0.1,0.2,0.3");

        String result = cacheStore.get("abc123");

        assertThat(result).isEqualTo("0.1,0.2,0.3");
    }

    @Test
    @DisplayName("DOIT retourner null quand la clé est absente de Redis (cache miss)")
    void shouldReturnNullOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        String result = cacheStore.get("inexistant");

        assertThat(result).isNull();
    }

    // -------------------------------------------------------------------------
    // save — écriture avec TTL
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT appeler redisTemplate.opsForValue().set avec le TTL en heures")
    void shouldCallSetWithTtlInHours() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        cacheStore.save("hash1", "0.1,0.2", 168);

        verify(valueOps).set(anyString(), eq("0.1,0.2"), eq(168L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("DOIT utiliser une clé distincte pour chaque hash")
    void shouldUseDifferentKeysForDifferentHashes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        cacheStore.save("hash1", "v1", 168);
        cacheStore.save("hash2", "v2", 168);

        verify(valueOps, times(2)).set(anyString(), anyString(), anyLong(), any());
    }

    // -------------------------------------------------------------------------
    // exists
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner true quand la clé existe")
    void shouldReturnTrueWhenKeyExists() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.TRUE);

        assertThat(cacheStore.exists("hash1")).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false quand la clé est absente")
    void shouldReturnFalseWhenKeyAbsent() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.FALSE);

        assertThat(cacheStore.exists("hash1")).isFalse();
    }

    // -------------------------------------------------------------------------
    // trackBatch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT ignorer silencieusement un batchId null ou vide")
    void shouldIgnoreBlankBatchId() {
        cacheStore.trackBatch(null, "hash1", 168);
        cacheStore.trackBatch("", "hash1", 168);
        cacheStore.trackBatch("  ", "hash1", 168);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("DOIT associer le hash au batch dans le Redis Set avec TTL")
    void shouldAddHashToBatchSetWithTtl() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        cacheStore.trackBatch("batch-001", "hash1", 168);

        verify(setOps).add(anyString(), eq("hash1"));
        verify(redisTemplate).expire(anyString(), eq(168L), eq(TimeUnit.HOURS));
    }

    // -------------------------------------------------------------------------
    // deleteByBatchId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT supprimer les 3 entrées associées au batch et retourner 3")
    void shouldDeleteAllEntriesForBatchAndReturn3() {
        Set<String> hashes = Set.of("hash1", "hash2", "hash3");
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(hashes);
        when(redisTemplate.delete(anyString())).thenReturn(Boolean.TRUE);

        int deleted = cacheStore.deleteByBatchId("batch-001");

        assertThat(deleted).isEqualTo(3);
        verify(redisTemplate, times(4)).delete(anyString()); // 3 hashes + 1 batchKey
    }

    @Test
    @DisplayName("DOIT retourner 0 pour un batchId inexistant sans lever d'exception (idempotent)")
    void shouldReturn0ForNonExistentBatchIdWithoutException() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(null);

        int deleted = cacheStore.deleteByBatchId("batch-inexistant");

        assertThat(deleted).isZero();
    }

    @Test
    @DisplayName("DOIT être idempotent : un second deleteByBatchId retourne 0")
    void shouldBeIdempotentOnSecondDelete() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Set.of());

        int firstDelete  = cacheStore.deleteByBatchId("batch-001");
        int secondDelete = cacheStore.deleteByBatchId("batch-001");

        assertThat(firstDelete).isZero();
        assertThat(secondDelete).isZero();
    }

    @Test
    @DisplayName("DOIT ne compter que les suppressions réussies dans deleteByBatchId")
    void shouldCountOnlySuccessfulDeletionsInDeleteByBatchId() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Set.of("hash1", "hash2"));
        when(redisTemplate.delete(contains("hash1"))).thenReturn(Boolean.TRUE);
        when(redisTemplate.delete(contains("hash2"))).thenReturn(Boolean.FALSE);

        int deleted = cacheStore.deleteByBatchId("batch-partial");

        assertThat(deleted).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // delete — suppression d'un embedding par hash
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT appeler redisTemplate.delete() avec la clé correspondant au hash")
    void shouldCallDeleteWithHashKey() {
        cacheStore.delete("hash-xyz");

        verify(redisTemplate).delete(anyString());
    }

    // -------------------------------------------------------------------------
    // deleteAll — suppression par pattern
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 0 pour deleteAll() quand aucune clé ne correspond")
    void shouldReturn0ForDeleteAllWhenNoKeysMatch() {
        when(redisTemplate.keys(anyString())).thenReturn(null);

        int deleted = cacheStore.deleteAll();

        assertThat(deleted).isZero();
    }

    @Test
    @DisplayName("DOIT supprimer les clés emb:* et batch:emb:* et retourner le total")
    void shouldDeleteEmbAndBatchPatternsAndReturnTotal() {
        Set<String> embKeys   = Set.of("emb:h1", "emb:h2");
        Set<String> batchKeys = Set.of("batch:emb:b1");
        when(redisTemplate.keys(contains("emb:"))).thenReturn(embKeys).thenReturn(batchKeys);
        when(redisTemplate.delete(anySet())).thenReturn(2L).thenReturn(1L);

        int deleted = cacheStore.deleteAll();

        assertThat(deleted).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // countBatch — statistiques
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner la taille du set Redis pour countBatch()")
    void shouldReturnRedisSetSizeForCountBatch() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size(anyString())).thenReturn(5L);

        assertThat(cacheStore.countBatch("batch-001")).isEqualTo(5L);
    }

    @Test
    @DisplayName("DOIT retourner 0 pour countBatch() quand Redis retourne null")
    void shouldReturn0ForCountBatchWhenRedisReturnsNull() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size(anyString())).thenReturn(null);

        assertThat(cacheStore.countBatch("batch-inconnu")).isZero();
    }
}
