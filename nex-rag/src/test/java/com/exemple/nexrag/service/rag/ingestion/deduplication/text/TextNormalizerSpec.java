package com.exemple.nexrag.service.rag.ingestion.deduplication.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : TextNormalizer — Normalisation et hachage SHA-256 hex")
class TextNormalizerSpec {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    @DisplayName("DOIT être idempotent : même texte → même hash à chaque appel")
    void devraitEtreIdempotent() {
        String h1 = normalizer.hash("Bonjour le monde");
        String h2 = normalizer.hash("Bonjour le monde");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("DOIT ignorer la casse : 'HELLO' et 'hello' donnent le même hash")
    void devraitIgnorerLaCasse() {
        assertThat(normalizer.hash("HELLO WORLD"))
            .isEqualTo(normalizer.hash("hello world"))
            .isEqualTo(normalizer.hash("Hello World"));
    }

    @Test
    @DisplayName("DOIT ignorer les espaces en début et en fin de texte")
    void devraitIgnorerEspacesExternes() {
        assertThat(normalizer.hash("bonjour"))
            .isEqualTo(normalizer.hash("  bonjour  "))
            .isEqualTo(normalizer.hash("\tbonjour\t"));
    }

    @Test
    @DisplayName("DOIT normaliser les espaces multiples en espace simple")
    void devraitNormaliserEspacesMultiples() {
        assertThat(normalizer.hash("hello world"))
            .isEqualTo(normalizer.hash("hello  world"))
            .isEqualTo(normalizer.hash("hello   world"));
    }

    @Test
    @DisplayName("DOIT retourner une chaîne hexadécimale minuscule de 64 caractères")
    void devraitRetournerHashHex64Caracteres() {
        String hash = normalizer.hash("texte quelconque");
        assertThat(hash)
            .hasSize(64)
            .matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("DOIT retourner des hashs distincts pour des textes sémantiquement différents")
    void devraitRetournerHashsDifferentsPourTextesDistincts() {
        assertThat(normalizer.hash("texte A"))
            .isNotEqualTo(normalizer.hash("texte B"));
    }

    @Test
    @DisplayName("DOIT traiter un texte vide sans lever d'exception")
    void devraitTraiterTexteVideSansException() {
        String hash = normalizer.hash("");
        assertThat(hash).isNotNull().hasSize(64);
    }

    @Test
    @DisplayName("DOIT traiter un texte composé uniquement d'espaces")
    void devraitTraiterTexteEspacesSeulement() {
        // "   ".trim() == "" → même hash que le texte vide
        assertThat(normalizer.hash("   "))
            .isEqualTo(normalizer.hash(""));
    }
}
