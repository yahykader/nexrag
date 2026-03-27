package com.exemple.nexrag.service.rag.ingestion.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : FileUtils — Utilitaires fichiers")
class FileUtilsSpec {

    @Test
    @DisplayName("DOIT retourner l'extension en minuscules pour un nom de fichier valide")
    void devraitRetournerExtensionEnMinuscules() {
        assertThat(FileUtils.getExtension("document.PDF")).isEqualTo("pdf");
        assertThat(FileUtils.getExtension("image.JPEG")).isEqualTo("jpeg");
    }

    @Test
    @DisplayName("DOIT retourner une chaîne vide quand le fichier n'a pas d'extension")
    void devraitRetournerVideSansExtension() {
        assertThat(FileUtils.getExtension("fichier")).isEmpty();
        assertThat(FileUtils.getExtension("fichier.")).isEmpty();
    }

    @Test
    @DisplayName("DOIT retourner une chaîne vide pour un nom null ou vide")
    void devraitRetournerVidePourNullOuVide() {
        assertThat(FileUtils.getExtension(null)).isEmpty();
        assertThat(FileUtils.getExtension("")).isEmpty();
        assertThat(FileUtils.getExtension("   ")).isEmpty();
    }

    @Test
    @DisplayName("DOIT remplacer les caractères spéciaux par des underscores")
    void devraitSanitiserNomFichier() {
        assertThat(FileUtils.sanitizeFilename("mon fichier (1).pdf"))
            .matches("[a-zA-Z0-9_-]+");
    }

    @Test
    @DisplayName("DOIT retourner 'unknown' pour un nom null ou vide lors de la sanitisation")
    void devraitRetournerUnknownPourNomVide() {
        assertThat(FileUtils.sanitizeFilename(null)).isEqualTo("unknown");
        assertThat(FileUtils.sanitizeFilename("")).isEqualTo("unknown");
    }

    @Test
    @DisplayName("DOIT formater la taille en bytes pour une valeur inférieure à 1024")
    void devraitFormaterEnBytes() {
        assertThat(FileUtils.formatFileSize(512)).contains("B");
        assertThat(FileUtils.formatFileSize(512)).doesNotContain("KB");
    }

    @Test
    @DisplayName("DOIT formater la taille en KB pour une valeur entre 1024 et 1MB")
    void devraitFormaterEnKB() {
        assertThat(FileUtils.formatFileSize(2048)).contains("KB");
    }

    @Test
    @DisplayName("DOIT retirer l'extension d'un nom de fichier")
    void devraitRetirerExtension() {
        assertThat(FileUtils.removeExtension("document.pdf")).isEqualTo("document");
        assertThat(FileUtils.removeExtension("archive.tar.gz")).isEqualTo("archive.tar");
    }

    @Test
    @DisplayName("DOIT retourner le nom inchangé s'il n'y a pas d'extension")
    void devraitRetournerNomSansExtensionInchange() {
        assertThat(FileUtils.removeExtension("fichier")).isEqualTo("fichier");
    }

    @Test
    @DisplayName("DOIT générer un nom d'image avec index")
    void devraitGenererNomImage() {
        String name = FileUtils.generateImageName("doc", "png", 3);
        assertThat(name).isEqualTo("doc_img_3.png");
    }

    @Test
    @DisplayName("DOIT générer un nom de page avec numéro de page")
    void devraitGenererNomPage() {
        String name = FileUtils.generatePageName("rapport", "pdf", 5);
        assertThat(name).isEqualTo("rapport_page_5.pdf");
    }
}
