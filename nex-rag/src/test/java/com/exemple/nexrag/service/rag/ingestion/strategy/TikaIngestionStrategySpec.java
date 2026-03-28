package com.exemple.nexrag.service.rag.ingestion.strategy;

import com.exemple.nexrag.service.rag.ingestion.strategy.commun.EmbeddingIndexer;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.IngestionLifecycle;
import com.exemple.nexrag.service.rag.ingestion.util.MetadataSanitizer;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Spec : TikaIngestionStrategy — Stratégie fallback universelle (1000+ formats).
 */
@DisplayName("Spec : TikaIngestionStrategy — Stratégie fallback universelle Apache Tika")
@ExtendWith(MockitoExtension.class)
class TikaIngestionStrategySpec {

    @Mock private EmbeddingStore<TextSegment> textStore;
    @Mock private MetadataSanitizer           sanitizer;
    @Mock private RAGMetrics                  ragMetrics;
    @Mock private EmbeddingIndexer            embeddingIndexer;
    @Mock private IngestionLifecycle          lifecycle;

    private TikaIngestionStrategy strategy() {
        return new TikaIngestionStrategy(
            textStore, sanitizer, ragMetrics, embeddingIndexer, lifecycle
        );
    }

    // -------------------------------------------------------------------------
    // canHandle — fallback universel
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT accepter tout type de fichier (fallback universel)")
    void shouldHandleAnyFile() {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, "quelconque")).isTrue();
    }

    @ParameterizedTest(name = "DOIT accepter l''extension ''{0}'' comme fallback")
    @ValueSource(strings = {"pdf", "docx", "pptx", "odt", "epub", "mp3", "msg", "rtf", "dwg", "eml"})
    @DisplayName("DOIT accepter les formats spécialisés en tant que fallback")
    void shouldHandleSpecializedFormatsAsFallback(String extension) {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, extension)).isTrue();
    }

    // -------------------------------------------------------------------------
    // Métadonnées de la stratégie
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 'TIKA' comme nom de stratégie")
    void shouldReturnTikaAsStrategyName() {
        assertThat(strategy().getName()).isEqualTo("TIKA");
    }

    @Test
    @DisplayName("DOIT retourner 10 comme priorité (la plus basse — fallback de dernier recours)")
    void shouldReturnLowestPriority() {
        assertThat(strategy().getPriority()).isEqualTo(10);
    }

    @Test
    @DisplayName("DOIT avoir une priorité supérieure à TEXT (8) pour être sélectionné en dernier")
    void shouldHavePriorityGreaterThanTextStrategy() {
        assertThat(strategy().getPriority()).isGreaterThan(8);
    }

    // -------------------------------------------------------------------------
    // Capacités statiques
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT exposer la liste des formats supportés via getSupportedFormatExamples()")
    void shouldExposeSupportedFormats() {
        String[] formats = TikaIngestionStrategy.getSupportedFormatExamples();
        assertThat(formats).isNotEmpty();
        assertThat(formats).contains("doc", "ppt", "epub");
    }

    @Test
    @DisplayName("DOIT exposer une description de ses capacités via getCapabilities()")
    void shouldExposeCapabilitiesDescription() {
        assertThat(TikaIngestionStrategy.getCapabilities()).contains("Tika");
    }
}
