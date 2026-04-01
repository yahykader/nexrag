package com.exemple.nexrag.service.rag.cache.metrics;

import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CacheService")
class CacheServiceSpec {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Mock
    private RAGMetrics ragMetrics;

    @InjectMocks
    private CacheService cacheService;

    // =========================================================================
    // get()
    // =========================================================================

    @Nested
    @DisplayName("get() — lecture du cache")
    class Get {

        @BeforeEach
        void setup() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
        }

        @Test
        void shouldReturnValueAndRecordHitWhenKeyExists() {
            when(valueOps.get("embedding:abc")).thenReturn("valeur");

            Optional<String> result = cacheService.get("embedding:abc", String.class);

            assertThat(result).isPresent().hasValue("valeur");
            verify(ragMetrics).recordCacheHit("embedding");
        }

        @Test
        void shouldReturnEmptyAndRecordMissWhenKeyAbsent() {
            when(valueOps.get("embedding:abc")).thenReturn(null);

            Optional<String> result = cacheService.get("embedding:abc", String.class);

            assertThat(result).isEmpty();
            verify(ragMetrics).recordCacheMiss("embedding");
        }

        @Test
        void shouldReturnEmptyAndRecordMissOnRedisError() {
            when(valueOps.get(any())).thenThrow(new RuntimeException());

            Optional<String> result = cacheService.get("embedding:abc", String.class);

            assertThat(result).isEmpty();
            verify(ragMetrics).recordCacheMiss("embedding");
        }

        @Test
        void shouldDetectQueryCacheType() {
            when(valueOps.get("query:xyz")).thenReturn(null);

            cacheService.get("query:xyz", String.class);

            verify(ragMetrics).recordCacheMiss("query");
        }

        @Test
        void shouldDetectOtherCacheType() {
            when(valueOps.get("custom:key")).thenReturn(null);

            cacheService.get("custom:key", String.class);

            verify(ragMetrics).recordCacheMiss("other");
        }
    }

    // =========================================================================
    // put()
    // =========================================================================

    @Nested
    @DisplayName("put() — écriture")
    class Put {

        @BeforeEach
        void setup() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
        }

        @Test
        void shouldStoreValueWithDefaultTtl() {
            cacheService.put("ma-cle", "ma-valeur");

            verify(valueOps).set("ma-cle", "ma-valeur", Duration.ofHours(24));
        }

        @Test
        void shouldStoreValueWithCustomTtl() {
            Duration ttl = Duration.ofMinutes(30);

            cacheService.put("ma-cle", "ma-valeur", ttl);

            verify(valueOps).set("ma-cle", "ma-valeur", ttl);
        }

        @Test
        void shouldAbsorbRedisExceptionOnPut() {
            doThrow(new RuntimeException())
                .when(valueOps).set(any(), any(), any(Duration.class));

            assertThatNoException().isThrownBy(() ->
                cacheService.put("cle", "valeur")
            );
        }
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        void shouldDeleteKey() {
            cacheService.delete("ma-cle");

            verify(redisTemplate).delete("ma-cle");
        }

        @Test
        void shouldAbsorbRedisException() {
            doThrow(new RuntimeException())
                .when(redisTemplate).delete(anyString());

            assertThatNoException().isThrownBy(() ->
                cacheService.delete("ma-cle")
            );
        }
    }

    // =========================================================================
    // exists()
    // =========================================================================

    @Nested
    @DisplayName("exists()")
    class Exists {

        @Test
        void shouldReturnTrue() {
            when(redisTemplate.hasKey("ma-cle")).thenReturn(true);

            assertThat(cacheService.exists("ma-cle")).isTrue();
        }

        @Test
        void shouldReturnFalse() {
            when(redisTemplate.hasKey("ma-cle")).thenReturn(false);

            assertThat(cacheService.exists("ma-cle")).isFalse();
        }

        @Test
        void shouldReturnFalseOnError() {
            when(redisTemplate.hasKey(any())).thenThrow(new RuntimeException());

            assertThat(cacheService.exists("ma-cle")).isFalse();
        }
    }

    // =========================================================================
    // Embeddings
    // =========================================================================

    @Nested
    @DisplayName("Embeddings")
    class Embeddings {

        @BeforeEach
        void setup() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
        }

        @Test
        void shouldStoreEmbeddingWithPrefixAndTtl() {
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

            cacheService.cacheEmbedding("texte", new float[]{1f});

            verify(valueOps).set(
                keyCaptor.capture(),
                any(),
                eq(Duration.ofDays(7))
            );

            assertThat(keyCaptor.getValue()).startsWith("embedding:");
        }

        @Test
        void shouldProduceSameKeyForSameText() {
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

            cacheService.cacheEmbedding("texte", "value");

            verify(valueOps).set(
                keyCaptor.capture(),
                any(),
                any(Duration.class)
            );

            String key = keyCaptor.getValue();

            when(valueOps.get(key)).thenReturn("value");

            Optional<String> result = cacheService.getEmbedding("texte", String.class);

            assertThat(result).isPresent().hasValue("value");
        }

        @Test
        void shouldProduceDifferentKeys() {
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

            cacheService.cacheEmbedding("A", "A");
            cacheService.cacheEmbedding("B", "B");

            verify(valueOps, times(2)).set(
                keyCaptor.capture(),
                any(),
                any(Duration.class)
            );

            var keys = keyCaptor.getAllValues();

            assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
        }
    }

    // =========================================================================
    // QueryResults
    // =========================================================================

    @Nested
    @DisplayName("QueryResults")
    class QueryResults {

        @BeforeEach
        void setup() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
        }

        @Test
        void shouldStoreQueryResult() {
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

            cacheService.cacheQueryResult("requete", "resultat");

            verify(valueOps).set(
                keyCaptor.capture(),
                any(),
                eq(Duration.ofHours(1))
            );

            assertThat(keyCaptor.getValue()).startsWith("query:");
        }

        @Test
        void shouldRetrieveQueryResult() {
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

            cacheService.cacheQueryResult("requete", "resultat");

            verify(valueOps).set(
                keyCaptor.capture(),
                any(),
                any(Duration.class)
            );

            String key = keyCaptor.getValue();

            when(valueOps.get(key)).thenReturn("resultat");

            Optional<String> result = cacheService.getQueryResult("requete", String.class);

            assertThat(result).isPresent().hasValue("resultat");
            verify(ragMetrics).recordCacheHit("query");
        }
    }

    // =========================================================================
    // clear()
    // =========================================================================

    @Nested
    @DisplayName("clear()")
    class Clear {

        @Test
        void shouldDeleteOnlyRagKeys() {
            Set<String> embeddingKeys = Set.of("embedding:a");
            Set<String> queryKeys = Set.of("query:b");

            when(redisTemplate.keys("embedding:*")).thenReturn(embeddingKeys);
            when(redisTemplate.keys("query:*")).thenReturn(queryKeys);

            cacheService.clear();

            verify(redisTemplate).delete(embeddingKeys);
            verify(redisTemplate).delete(queryKeys);
        }

        @Test
        void shouldHandleErrors() {
            when(redisTemplate.keys(any())).thenThrow(new RuntimeException());

            assertThatNoException().isThrownBy(() ->
                cacheService.clear()
            );
        }

        @Test
        void shouldHandleEmpty() {
            when(redisTemplate.keys("embedding:*")).thenReturn(Set.of());
            when(redisTemplate.keys("query:*")).thenReturn(Set.of());

            cacheService.clear();

            verify(redisTemplate, never()).delete(anyCollection());
        }
    }
}