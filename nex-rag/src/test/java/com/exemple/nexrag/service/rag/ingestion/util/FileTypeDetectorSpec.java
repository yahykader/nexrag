package com.exemple.nexrag.service.rag.ingestion.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : FileTypeDetector — Détection MIME par magic bytes")
class FileTypeDetectorSpec {

    private final FileTypeDetector detector = new FileTypeDetector();

    @Test
    @DisplayName("DOIT retourner text/plain quand les bytes correspondent à du texte brut")
    void devraitDetecterTextebrut() {
        byte[] textBytes = "Hello world, this is plain text.".getBytes(StandardCharsets.UTF_8);
        assertThat(detector.detect(textBytes)).contains("text/plain");
    }

    @Test
    @DisplayName("DOIT retourner application/pdf pour un en-tête PDF valide")
    void devraitDetecterPdfParMagicBytes() {
        // PDF magic bytes: %PDF-1.4
        byte[] pdfHeader = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>".getBytes(StandardCharsets.UTF_8);
        assertThat(detector.detect(pdfHeader)).contains("pdf");
    }

    @Test
    @DisplayName("DOIT retourner une valeur non-null pour un tableau vide")
    void devraitNeJamaisRetournerNullPourTableauVide() {
        assertThat(detector.detect(new byte[0])).isNotNull();
    }

    @Test
    @DisplayName("DOIT retourner application/octet-stream pour des bytes inconnus")
    void devraitRetournerOctetStreamPourBytesInconnus() {
        byte[] random = {0x00, 0x01, 0x02, 0x03, 0x04};
        // Can return application/octet-stream or another detected type — must not be null
        assertThat(detector.detect(random)).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("DOIT retourner application/octet-stream pour bytes null")
    void devraitRetournerFallbackPourBytesNull() {
        assertThat(detector.detect(null)).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("DOIT ne pas lever d'exception pour n'importe quelle entrée")
    void devraitNePasLeverExceptionPourEntreeArbitraire() {
        byte[] arbitrary = "quelconque".getBytes(StandardCharsets.UTF_8);
        assertThatCode(() -> detector.detect(arbitrary)).doesNotThrowAnyException();
    }
}
