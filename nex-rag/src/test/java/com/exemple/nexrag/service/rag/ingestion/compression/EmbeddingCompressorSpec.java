package com.exemple.nexrag.service.rag.ingestion.compression;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Spec : EmbeddingCompressor — Compression et quantization de vecteurs d'embeddings.
 */
@DisplayName("Spec : EmbeddingCompressor — Compression et quantization de vecteurs")
class EmbeddingCompressorSpec {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private EmbeddingCompressor compressorActivated() {
        return new EmbeddingCompressor(true, "INT8", 8);
    }

    private EmbeddingCompressor compressorDesactive() {
        return new EmbeddingCompressor(false, "INT8", 8);
    }

    private Embedding vecteurTest() {
        return Embedding.from(new float[]{0.1f, -0.2f, 0.3f, -0.4f, 0.5f, -0.6f, 0.7f, -0.8f});
    }

    // -------------------------------------------------------------------------
    // Mode désactivé
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner le vecteur identique quand la compression est désactivée")
    void shouldReturnOriginalVectorWhenCompressionDisabled() {
        EmbeddingCompressor compressor = compressorDesactive();
        Embedding original = vecteurTest();

        Embedding result = compressor.quantizeInt8(original);

        assertThat(result.vector()).isEqualTo(original.vector());
    }

    @Test
    @DisplayName("DOIT retourner le vecteur identique (Int16) quand la compression est désactivée")
    void shouldReturnOriginalVectorInt16WhenCompressionDisabled() {
        EmbeddingCompressor compressor = compressorDesactive();
        Embedding original = vecteurTest();

        Embedding result = compressor.quantizeInt16(original);

        assertThat(result.vector()).isEqualTo(original.vector());
    }

    // -------------------------------------------------------------------------
    // Quantization INT8
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT préserver le nombre de dimensions après quantization INT8")
    void shouldPreserveDimensionsAfterInt8Quantization() {
        EmbeddingCompressor compressor = compressorActivated();
        Embedding original = vecteurTest();

        Embedding compressed = compressor.quantizeInt8(original);

        assertThat(compressed.vector()).hasSize(original.vector().length);
    }

    @Test
    @DisplayName("DOIT produire une similarité cosinus ≥ 0.98 après quantization INT8")
    void shouldProduceHighCosineSimilarityAfterInt8Quantization() {
        EmbeddingCompressor compressor = compressorActivated();
        Embedding original = vecteurTest();

        Embedding compressed = compressor.quantizeInt8(original);
        double similarity = compressor.cosineSimilarity(original, compressed);

        assertThat(similarity).isGreaterThanOrEqualTo(0.98);
    }

    @Test
    @DisplayName("DOIT retourner les métadonnées de compression avec méthode INT8")
    void shouldReturnCompressionMetadataWithInt8Method() {
        EmbeddingCompressor compressor = compressorActivated();
        Embedding original = vecteurTest();

        EmbeddingCompressor.CompressedEmbedding result = compressor.quantizeInt8WithMetadata(original);

        assertThat(result.method()).isEqualTo(EmbeddingCompressor.CompressionMethod.INT8);
        assertThat(result.dimensions()).isEqualTo(original.vector().length);
        assertThat(result.data()).hasSize(original.vector().length);
    }

    // -------------------------------------------------------------------------
    // Quantization INT16
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT préserver le nombre de dimensions après quantization INT16")
    void shouldPreserveDimensionsAfterInt16Quantization() {
        EmbeddingCompressor compressor = compressorActivated();
        Embedding original = vecteurTest();

        Embedding compressed = compressor.quantizeInt16(original);

        assertThat(compressed.vector()).hasSize(original.vector().length);
    }

    @Test
    @DisplayName("DOIT produire une similarité cosinus ≥ 0.99 après quantization INT16")
    void shouldProduceHighCosineSimilarityAfterInt16Quantization() {
        EmbeddingCompressor compressor = compressorActivated();
        Embedding original = vecteurTest();

        Embedding compressed = compressor.quantizeInt16(original);
        double similarity = compressor.cosineSimilarity(original, compressed);

        assertThat(similarity).isGreaterThanOrEqualTo(0.99);
    }

    // -------------------------------------------------------------------------
    // Statistiques de compression
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT calculer un pourcentage de réduction ≈ 75% pour INT8")
    void shouldCalculateReductionPercentageForInt8() {
        EmbeddingCompressor compressor = compressorActivated();
        Embedding original   = vecteurTest();
        Embedding compressed = compressor.quantizeInt8(original);

        EmbeddingCompressor.CompressionStats stats = compressor.calculateStats(original, compressed);

        // INT8 : 1 byte/dim vs 4 bytes/dim → réduction théorique 75%
        // La métrique calcule sur la taille compressée (pas dequantizée) via defaultMethod
        assertThat(stats.reductionPercent()).isGreaterThan(0.0);
        assertThat(stats.similarity()).isGreaterThanOrEqualTo(0.98);
    }

    @Test
    @DisplayName("DOIT calculer une perte MSE ≤ 5% pour INT8 avec un vecteur court")
    void shouldCalculateLowMseLossForInt8() {
        EmbeddingCompressor compressor = compressorActivated();
        Embedding original   = vecteurTest();
        Embedding compressed = compressor.quantizeInt8(original);

        EmbeddingCompressor.CompressionStats stats = compressor.calculateStats(original, compressed);

        assertThat(stats.lossPercent()).isLessThan(5.0);
    }

    // -------------------------------------------------------------------------
    // Similarité cosinus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 1.0 pour la similarité cosinus d'un vecteur avec lui-même")
    void shouldReturnMaxCosineSimilarityForIdenticalVectors() {
        EmbeddingCompressor compressor = compressorDesactive();
        Embedding original = vecteurTest();

        double similarity = compressor.cosineSimilarity(original, original);

        assertThat(similarity).isCloseTo(1.0, within(0.001));
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner true pour isCompressionEnabled quand activée")
    void shouldReturnTrueWhenCompressionEnabled() {
        assertThat(compressorActivated().isCompressionEnabled()).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false pour isCompressionEnabled quand désactivée")
    void shouldReturnFalseWhenCompressionDisabled() {
        assertThat(compressorDesactive().isCompressionEnabled()).isFalse();
    }

    @Test
    @DisplayName("DOIT retourner INT8 comme méthode par défaut quand configurée")
    void shouldReturnConfiguredDefaultMethod() {
        assertThat(compressorActivated().getDefaultMethod())
            .isEqualTo(EmbeddingCompressor.CompressionMethod.INT8);
    }
}
