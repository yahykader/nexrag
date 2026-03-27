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
import static org.mockito.Mockito.when;

/**
 * Spec : PdfIngestionStrategy — Stratégie d'ingestion pour fichiers PDF.
 */
@DisplayName("Spec : PdfIngestionStrategy — Ingestion de fichiers PDF")
@ExtendWith(MockitoExtension.class)
class PdfIngestionStrategySpec {

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

    private PdfIngestionStrategy strategy() {
        return new PdfIngestionStrategy(
            textStore, imageStore, visionAnalyzer, imageSaver,
            signatureValidator, sanitizer, ragMetrics,
            embeddingIndexer, textChunker, lifecycle
        );
    }

    // -------------------------------------------------------------------------
    // canHandle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT accepter l'extension 'pdf'")
    void shouldHandlePdfExtension() {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, "pdf")).isTrue();
    }

    @Test
    @DisplayName("DOIT rejeter l'extension 'docx'")
    void shouldRejectDocxExtension() {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, "docx")).isFalse();
    }

    @Test
    @DisplayName("DOIT rejeter l'extension 'txt'")
    void shouldRejectTxtExtension() {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, "txt")).isFalse();
    }

    @Test
    @DisplayName("DOIT rejeter une extension vide")
    void shouldRejectBlankExtension() {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, "")).isFalse();
    }

    // -------------------------------------------------------------------------
    // Métadonnées de la stratégie
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 'PDF' comme nom de stratégie")
    void shouldReturnPdfAsStrategyName() {
        assertThat(strategy().getName()).isEqualTo("PDF");
    }

    @Test
    @DisplayName("DOIT retourner 1 comme priorité (la plus haute parmi les stratégies spécialisées)")
    void shouldReturnPriority1() {
        assertThat(strategy().getPriority()).isEqualTo(1);
    }
}
