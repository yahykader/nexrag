package com.exemple.nexrag.service.rag.ingestion.cache;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Spec : EmbeddingSerializer — Sérialisation CSV aller-retour d'embeddings.
 */
@DisplayName("Spec : EmbeddingSerializer — Sérialisation/désérialisation CSV de vecteurs float[]")
class EmbeddingSerializerSpec {

    private final EmbeddingSerializer serializer = new EmbeddingSerializer();

    private Embedding embeddingTest() {
        return Embedding.from(new float[]{0.1f, -0.2f, 0.3f, -0.4f, 0.5f});
    }

    // -------------------------------------------------------------------------
    // Sérialisation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT produire une chaîne non vide pour un embedding valide")
    void shouldProduceNonEmptyStringForValidEmbedding() {
        String result = serializer.serialize(embeddingTest());
        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("DOIT séparer les valeurs par des virgules")
    void shouldSeparateValuesWithCommas() {
        Embedding embedding = Embedding.from(new float[]{1.0f, 2.0f, 3.0f});
        String result = serializer.serialize(embedding);
        assertThat(result.split(",")).hasSize(3);
    }

    @Test
    @DisplayName("DOIT produire la même chaîne pour le même embedding (déterministe)")
    void shouldProduceSameStringForSameEmbedding() {
        Embedding embedding = embeddingTest();
        String result1 = serializer.serialize(embedding);
        String result2 = serializer.serialize(embedding);
        assertThat(result1).isEqualTo(result2);
    }

    // -------------------------------------------------------------------------
    // Désérialisation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT reconstituer un embedding avec le même nombre de dimensions")
    void shouldReconstituteSameDimensions() {
        Embedding original = embeddingTest();
        String serialized = serializer.serialize(original);
        Embedding restored = serializer.deserialize(serialized);
        assertThat(restored.vector()).hasSize(original.vector().length);
    }

    @Test
    @DisplayName("DOIT reconstituer les valeurs avec une précision acceptable (± 1e-6)")
    void shouldRestoreValuesWithAcceptablePrecision() {
        Embedding original = embeddingTest();
        String serialized = serializer.serialize(original);
        Embedding restored = serializer.deserialize(serialized);

        float[] orig = original.vector();
        float[] rest = restored.vector();
        for (int i = 0; i < orig.length; i++) {
            assertThat((double) rest[i]).isCloseTo(orig[i], within(1e-6));
        }
    }

    // -------------------------------------------------------------------------
    // Aller-retour complet
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT produire un vecteur identique après sérialisation puis désérialisation")
    void shouldProduceIdenticalVectorAfterRoundTrip() {
        Embedding original = embeddingTest();
        Embedding restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored.vector()).containsExactly(original.vector(), within(1e-6f));
    }

    @Test
    @DisplayName("DOIT préserver l'aller-retour pour un vecteur unitaire (dim=1)")
    void shouldPreserveRoundTripForSingleDimension() {
        Embedding single = Embedding.from(new float[]{42.0f});
        Embedding restored = serializer.deserialize(serializer.serialize(single));
        assertThat(restored.vector()[0]).isCloseTo(42.0f, within(1e-6f));
    }

    @Test
    @DisplayName("DOIT gérer un vecteur avec valeurs négatives")
    void shouldHandleNegativeValues() {
        Embedding negative = Embedding.from(new float[]{-1.5f, -0.001f, -100.0f});
        Embedding restored = serializer.deserialize(serializer.serialize(negative));
        assertThat(restored.vector()[0]).isCloseTo(-1.5f, within(1e-6f));
        assertThat(restored.vector()[2]).isCloseTo(-100.0f, within(1e-6f));
    }
}
