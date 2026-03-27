package com.exemple.nexrag.service.rag.ingestion.strategy;

import com.exemple.nexrag.service.rag.ingestion.strategy.commun.EmbeddingIndexer;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.IngestionLifecycle;
import com.exemple.nexrag.service.rag.ingestion.util.MetadataSanitizer;
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
 * Spec : TextIngestionStrategy — Stratégie d'ingestion pour fichiers texte (40+ formats).
 */
@DisplayName("Spec : TextIngestionStrategy — Ingestion de fichiers texte (40+ formats)")
@ExtendWith(MockitoExtension.class)
class TextIngestionStrategySpec {

    @Mock private EmbeddingStore<TextSegment> textStore;
    @Mock private MetadataSanitizer           sanitizer;
    @Mock private EmbeddingIndexer            embeddingIndexer;
    @Mock private IngestionLifecycle          lifecycle;

    private TextIngestionStrategy strategy() {
        return new TextIngestionStrategy(textStore, sanitizer, embeddingIndexer, lifecycle);
    }

    // -------------------------------------------------------------------------
    // canHandle — extensions supportées
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "DOIT accepter l''extension ''{0}''")
    @ValueSource(strings = {"txt", "md", "csv", "json", "xml", "java", "py", "js", "ts", "log", "yaml", "sql"})
    @DisplayName("DOIT accepter les extensions texte standard")
    void shouldHandleSupportedExtensions(String extension) {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, extension)).isTrue();
    }

    @ParameterizedTest(name = "DOIT rejeter l''extension ''{0}''")
    @ValueSource(strings = {"pdf", "docx", "xlsx", "pptx", "png", "jpg", "mp4", "zip"})
    @DisplayName("DOIT rejeter les extensions non-texte")
    void shouldRejectNonTextExtensions(String extension) {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, extension)).isFalse();
    }

    @Test
    @DisplayName("DOIT accepter 'markdown' comme alias de 'md'")
    void shouldHandleMarkdownAlias() {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, "markdown")).isTrue();
    }

    @Test
    @DisplayName("DOIT accepter les fichiers de configuration (.properties, .conf, .ini)")
    void shouldHandleConfigurationFileExtensions() {
        MultipartFile file = mock(MultipartFile.class);
        assertThat(strategy().canHandle(file, "properties")).isTrue();
        assertThat(strategy().canHandle(file, "conf")).isTrue();
        assertThat(strategy().canHandle(file, "ini")).isTrue();
    }

    // -------------------------------------------------------------------------
    // Métadonnées de la stratégie
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 'TEXT' comme nom de stratégie")
    void shouldReturnTextAsStrategyName() {
        assertThat(strategy().getName()).isEqualTo("TEXT");
    }

    @Test
    @DisplayName("DOIT retourner 8 comme priorité (avant Tika, après Image)")
    void shouldReturnPriority8() {
        assertThat(strategy().getPriority()).isEqualTo(8);
    }

    // -------------------------------------------------------------------------
    // Utilitaires statiques
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT exposer l'ensemble des extensions supportées via getSupportedExtensions()")
    void shouldExposeSupportedExtensionsSet() {
        assertThat(TextIngestionStrategy.getSupportedExtensions()).isNotEmpty();
        assertThat(TextIngestionStrategy.getSupportedExtensions()).contains("txt", "md", "json");
    }

    @Test
    @DisplayName("DOIT retourner true pour isSupported sur une extension reconnue")
    void shouldReturnTrueForSupportedExtension() {
        assertThat(TextIngestionStrategy.isSupported("java")).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false pour isSupported sur une extension inconnue")
    void shouldReturnFalseForUnsupportedExtension() {
        assertThat(TextIngestionStrategy.isSupported("pdf")).isFalse();
    }
}
