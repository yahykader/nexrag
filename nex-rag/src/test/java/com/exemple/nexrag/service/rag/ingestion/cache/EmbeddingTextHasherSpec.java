package com.exemple.nexrag.service.rag.ingestion.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec : EmbeddingTextHasher — Hachage déterministe de texte pour clés Redis.
 */
@DisplayName("Spec : EmbeddingTextHasher — Hachage SHA-256 tronqué pour clés Redis")
class EmbeddingTextHasherSpec {

    private final EmbeddingTextHasher hasher = new EmbeddingTextHasher();

    @Test
    @DisplayName("DOIT retourner le même hash pour un texte identique (déterministe)")
    void shouldReturnSameHashForIdenticalText() {
        String hash1 = hasher.hash("Bonjour le monde");
        String hash2 = hasher.hash("Bonjour le monde");

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("DOIT retourner des hashs différents pour des textes différents")
    void shouldReturnDifferentHashesForDifferentTexts() {
        String hash1 = hasher.hash("Texte A");
        String hash2 = hasher.hash("Texte B");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("DOIT retourner un hash de longueur 16 caractères hexadécimaux")
    void shouldReturnHashOfLength16() {
        String hash = hasher.hash("quelque chose");

        assertThat(hash).hasSize(16);
        assertThat(hash).matches("[0-9a-f]{16}");
    }

    @Test
    @DisplayName("DOIT être sensible à la casse (Hello ≠ hello)")
    void shouldBeCaseSensitive() {
        String hashUpper = hasher.hash("Hello");
        String hashLower = hasher.hash("hello");

        assertThat(hashUpper).isNotEqualTo(hashLower);
    }

    @Test
    @DisplayName("DOIT traiter une chaîne vide sans exception")
    void shouldHandleEmptyStringWithoutException() {
        String hash = hasher.hash("");

        assertThat(hash).hasSize(16);
    }

    @Test
    @DisplayName("DOIT être sensible aux espaces (texte avec espaces ≠ texte sans espaces)")
    void shouldBeSensitiveToWhitespace() {
        String hashWithSpaces    = hasher.hash("hello world");
        String hashWithoutSpaces = hasher.hash("helloworld");

        assertThat(hashWithSpaces).isNotEqualTo(hashWithoutSpaces);
    }
}
