package com.exemple.nexrag.service.rag.ingestion.strategy;

import com.exemple.nexrag.service.rag.ingestion.analyzer.ImageSaver;
import com.exemple.nexrag.service.rag.ingestion.analyzer.VisionAnalyzer;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.EmbeddingIndexer;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.IngestionLifecycle;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.TextChunker;
import com.exemple.nexrag.service.rag.ingestion.util.MetadataSanitizer;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import com.exemple.nexrag.validation.FileSignatureValidator;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Spec : DocxIngestionStrategy — Stratégie d'ingestion pour fichiers DOCX.
 */
@DisplayName("Spec : DocxIngestionStrategy — Ingestion de fichiers DOCX")
@ExtendWith(MockitoExtension.class)
class DocxIngestionStrategySpec {

    @Mock private EmbeddingStore<TextSegment> textStore;
    @Mock private EmbeddingStore<TextSegment> imageStore;
    @Mock private VisionAnalyzer              visionAnalyzer;
    @Mock private ImageSaver                  imageSaver;
    @Mock private FileSignatureValidator      signatureValidator;
    @Mock private MetadataSanitizer           sanitizer;
    @Mock private RAGMetrics                  ragMetrics;
    @Mock private EmbeddingIndexer            embeddingIndexer;
    @Mock private TextChunker                 textChunker;
    @Mock private IngestionLifecycle          lifecycle;

    private DocxIngestionStrategy strategy() {
        return new DocxIngestionStrategy(
            textStore, imageStore, visionAnalyzer, imageSaver,
            signatureValidator, sanitizer, ragMetrics,
            embeddingIndexer, textChunker, lifecycle
        );
    }

    // -------------------------------------------------------------------------
    // canHandle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT accepter l'extension 'docx'")
    void shouldHandleDocxExtension() {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, "docx")).isTrue();
    }

    @Test
    @DisplayName("DOIT rejeter l'extension 'pdf'")
    void shouldRejectPdfExtension() {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, "pdf")).isFalse();
    }

    @Test
    @DisplayName("DOIT rejeter l'extension 'xlsx'")
    void shouldRejectXlsxExtension() {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, "xlsx")).isFalse();
    }

    @Test
    @DisplayName("DOIT rejeter l'extension 'doc' (format Word ancien non supporté)")
    void shouldRejectDocExtension() {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, "doc")).isFalse();
    }

    // -------------------------------------------------------------------------
    // Métadonnées de la stratégie
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 'DOCX' comme nom de stratégie")
    void shouldReturnDocxAsStrategyName() {
        assertThat(strategy().getName()).isEqualTo("DOCX");
    }

    @Test
    @DisplayName("DOIT retourner 2 comme priorité")
    void shouldReturnPriority2() {
        assertThat(strategy().getPriority()).isEqualTo(2);
    }
}
