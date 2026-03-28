package com.exemple.nexrag.service.rag.ingestion.strategy;

import com.exemple.nexrag.service.rag.ingestion.analyzer.ImageSaver;
import com.exemple.nexrag.service.rag.ingestion.analyzer.VisionAnalyzer;
import com.exemple.nexrag.service.rag.ingestion.cache.EmbeddingCache;
import com.exemple.nexrag.service.rag.ingestion.deduplication.file.DeduplicationService;
import com.exemple.nexrag.service.rag.ingestion.tracker.IngestionTracker;
import com.exemple.nexrag.service.rag.ingestion.util.MetadataSanitizer;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import com.exemple.nexrag.validation.FileSignatureValidator;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
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
 * Spec : ImageIngestionStrategy — Stratégie d'ingestion pour images.
 */
@DisplayName("Spec : ImageIngestionStrategy — Ingestion de fichiers images")
@ExtendWith(MockitoExtension.class)
class ImageIngestionStrategySpec {

    @Mock private EmbeddingStore<TextSegment> imageStore;
    @Mock private EmbeddingModel              embeddingModel;
    @Mock private VisionAnalyzer              visionAnalyzer;
    @Mock private ImageSaver                  imageSaver;
    @Mock private IngestionTracker            tracker;
    @Mock private MetadataSanitizer           sanitizer;
    @Mock private RAGMetrics                  ragMetrics;
    @Mock private DeduplicationService        deduplicationService;
    @Mock private FileSignatureValidator      signatureValidator;
    @Mock private EmbeddingCache              embeddingCache;

    private ImageIngestionStrategy strategy() {
        return new ImageIngestionStrategy(
            imageStore, embeddingModel, visionAnalyzer, imageSaver,
            tracker, sanitizer, ragMetrics, deduplicationService,
            signatureValidator, embeddingCache
        );
    }

    // -------------------------------------------------------------------------
    // canHandle — extensions images
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "DOIT accepter l''extension ''{0}''")
    @ValueSource(strings = {"png", "jpg", "jpeg", "gif", "bmp", "tiff", "tif", "webp", "svg"})
    @DisplayName("DOIT accepter toutes les extensions images supportées")
    void shouldHandleSupportedImageExtensions(String extension) {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, extension)).isTrue();
    }

    @ParameterizedTest(name = "DOIT rejeter l''extension ''{0}''")
    @ValueSource(strings = {"pdf", "docx", "txt", "mp4", "zip", "raw"})
    @DisplayName("DOIT rejeter les extensions non-image")
    void shouldRejectNonImageExtensions(String extension) {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, extension)).isFalse();
    }

    @Test
    @DisplayName("DOIT être insensible à la casse pour les extensions")
    void shouldBeCaseInsensitiveForExtensions() {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, "PNG")).isTrue();
        assertThat(strategy().canHandle(file, "JPG")).isTrue();
    }

    // -------------------------------------------------------------------------
    // Métadonnées de la stratégie
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 'IMAGE' comme nom de stratégie")
    void shouldReturnImageAsStrategyName() {
        assertThat(strategy().getName()).isEqualTo("IMAGE");
    }

    @Test
    @DisplayName("DOIT retourner 4 comme priorité")
    void shouldReturnPriority4() {
        assertThat(strategy().getPriority()).isEqualTo(4);
    }

    @Test
    @DisplayName("DOIT avoir une priorité entre DOCX (2) et TEXT (8)")
    void shouldHavePriorityBetweenDocxAndText() {
        int priority = strategy().getPriority();
        assertThat(priority).isGreaterThan(2).isLessThan(8);
    }
}
