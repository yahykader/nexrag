package com.exemple.nexrag.service.rag.ingestion.analyzer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Spec : ImageSaver — Sauvegarde d'images sur disque.
 */
@DisplayName("Spec : ImageSaver — Sauvegarde d'images sur disque")
@ExtendWith(MockitoExtension.class)
class ImageSaverSpec {

    @Mock
    private ImageStorageProperties props;

    @Mock
    private FilenameHandler filenameHandler;

    @InjectMocks
    private ImageSaver imageSaver;

    private BufferedImage imageValide() {
        return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    @DisplayName("DOIT lever IllegalArgumentException quand l'image est null")
    void shouldThrowWhenImageIsNull() throws IOException {
        assertThatThrownBy(() -> imageSaver.saveImage(null, "test"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DOIT lever IllegalArgumentException quand le nom d'image est null")
    void shouldThrowWhenImageNameIsNull() throws IOException {
        assertThatThrownBy(() -> imageSaver.saveImage(imageValide(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DOIT lever IllegalArgumentException quand le nom d'image est vide")
    void shouldThrowWhenImageNameIsBlank() throws IOException {
        assertThatThrownBy(() -> imageSaver.saveImage(imageValide(), "  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DOIT retourner le chemin absolu du fichier sauvegardé")
    void shouldReturnAbsolutePathAfterSaving() throws IOException {
        String tmpDir = System.getProperty("java.io.tmpdir");
        when(props.getStoragePath()).thenReturn(tmpDir);
        when(filenameHandler.sanitize("image_test")).thenReturn("image_test");

        String path = imageSaver.saveImage(imageValide(), "image_test");

        org.assertj.core.api.Assertions.assertThat(path).isNotBlank().endsWith(".png");
    }

    @Test
    @DisplayName("DOIT retourner le chemin de stockage configuré")
    void shouldReturnConfiguredStoragePath() {
        when(props.getStoragePath()).thenReturn("/tmp/images");
        org.assertj.core.api.Assertions.assertThat(imageSaver.getStoragePath()).isEqualTo("/tmp/images");
    }
}
