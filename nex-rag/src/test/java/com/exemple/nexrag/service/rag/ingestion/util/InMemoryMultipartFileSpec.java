package com.exemple.nexrag.service.rag.ingestion.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : InMemoryMultipartFile — Fichier multipart en mémoire")
class InMemoryMultipartFileSpec {

    @Test
    @DisplayName("DOIT retourner le nom original fourni au constructeur")
    void devraitRetournerNomOriginal() {
        InMemoryMultipartFile file = new InMemoryMultipartFile(
            "rapport.pdf", "application/pdf", new byte[]{1, 2, 3});
        assertThat(file.getOriginalFilename()).isEqualTo("rapport.pdf");
    }

    @Test
    @DisplayName("DOIT retourner le contenu bytes fourni au constructeur")
    void devraitRetournerContenuBytes() throws IOException {
        byte[] content = {10, 20, 30};
        InMemoryMultipartFile file = new InMemoryMultipartFile(
            "test.txt", "text/plain", content);
        assertThat(file.getBytes()).isEqualTo(content);
    }

    @Test
    @DisplayName("DOIT retourner isEmpty() == true pour un fichier de 0 bytes")
    void devraitEtreVidePourZeroBytes() {
        InMemoryMultipartFile file = new InMemoryMultipartFile(
            "vide.txt", "text/plain", new byte[0]);
        assertThat(file.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner isEmpty() == false pour un fichier non vide")
    void devraitNePasEtreVidePourFichierNonVide() {
        InMemoryMultipartFile file = new InMemoryMultipartFile(
            "doc.pdf", "application/pdf", new byte[]{1, 2, 3});
        assertThat(file.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("DOIT retourner la taille correcte via getSize()")
    void devraitRetournerTailleCorrecte() {
        byte[] content = new byte[42];
        InMemoryMultipartFile file = new InMemoryMultipartFile(
            "fichier.pdf", "application/pdf", content);
        assertThat(file.getSize()).isEqualTo(42L);
    }

    @Test
    @DisplayName("DOIT fournir un InputStream valide du contenu")
    void devraitFournirInputStream() throws IOException {
        byte[] content = {65, 66, 67};
        InMemoryMultipartFile file = new InMemoryMultipartFile(
            "test.txt", "text/plain", content);
        try (InputStream is = file.getInputStream()) {
            assertThat(is.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("DOIT lever UnsupportedOperationException pour transferTo()")
    void devraitLeverExceptionPourTransferTo() {
        InMemoryMultipartFile file = new InMemoryMultipartFile(
            "test.txt", "text/plain", new byte[]{1});
        assertThatThrownBy(() -> file.transferTo(new java.io.File("dest.txt")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("DOIT utiliser 'file' comme name par défaut quand null est fourni")
    void devraitUtiliserNomParDefautSiNull() {
        InMemoryMultipartFile file = new InMemoryMultipartFile(
            null, null, null, null);
        assertThat(file.getName()).isEqualTo("file");
        assertThat(file.getOriginalFilename()).isEqualTo("file");
        assertThat(file.getContentType()).isEqualTo("application/octet-stream");
    }
}
