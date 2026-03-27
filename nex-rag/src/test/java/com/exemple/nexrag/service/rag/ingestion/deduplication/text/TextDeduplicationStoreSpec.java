package com.exemple.nexrag.service.rag.ingestion.deduplication.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : TextDeduplicationStore — Accès Redis pour déduplication texte")
class TextDeduplicationStoreSpec {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private SetOperations<String, String> setOps;

    @InjectMocks
    private TextDeduplicationStore store;

    @Test
    @DisplayName("DOIT retourner true quand la clé existe dans Redis")
    void devraitRetournerTrueQuandCleExisteDansRedis() {
        when(redisTemplate.hasKey("text:dedup:abc")).thenReturn(Boolean.TRUE);
        assertThat(store.exists("text:dedup:abc")).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false quand la clé est absente de Redis")
    void devraitRetournerFalseQuandCleAbsente() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.FALSE);
        assertThat(store.exists("cle-absente")).isFalse();
    }

    @Test
    @DisplayName("DOIT retourner false et ne pas propager l'exception quand Redis est inaccessible")
    void devraitRetournerFallbackFalseQuandRedisInaccessible() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis down"));
        assertThat(store.exists("cle")).isFalse();
    }

    @Test
    @DisplayName("DOIT marquer une clé comme indexée via opsForValue().set() avec TTL")
    void devraitMarquerCleCommeIndexee() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store.markIndexed("text:dedup:abc", 30);
        verify(valueOps).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("DOIT absorber silencieusement les erreurs Redis lors du markIndexed")
    void devraitAbsorberErreursRedisLorsDuMarquage() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("Redis down"))
            .when(valueOps).set(anyString(), anyString(), any());

        assertThatCode(() -> store.markIndexed("cle", 30)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DOIT tracker le hash d'un batch via opsForSet().add()")
    void devraitTrackerHashDansBatch() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.expire(anyString(), any())).thenReturn(Boolean.TRUE);

        store.trackBatchHash("batch-001", "hashABC", 30);

        verify(setOps).add(anyString(), anyString());
    }
}
