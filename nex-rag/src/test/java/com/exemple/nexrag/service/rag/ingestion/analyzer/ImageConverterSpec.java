package com.exemple.nexrag.service.rag.ingestion.analyzer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec : ImageConverter — Conversion d'images BufferedImage en Base64.
 */
@DisplayName("Spec : ImageConverter — Conversion Base64 et type MIME")
@ExtendWith(MockitoExtension.class)
class ImageConverterSpec {

    @InjectMocks
    private ImageConverter imageConverter;

    private BufferedImage imageRgb() {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000); // pixel rouge
        return img;
    }

    @Test
    @DisplayName("DOIT retourner une chaîne Base64 non vide pour une image valide")
    void shouldReturnNonEmptyBase64ForValidImage() throws IOException {
        String base64 = imageConverter.toBase64(imageRgb());

        assertThat(base64).isNotBlank();
    }

    @Test
    @DisplayName("DOIT retourner le même Base64 pour la même image (déterministe)")
    void shouldReturnSameBase64ForSameImage() throws IOException {
        BufferedImage img = imageRgb();

        String base64a = imageConverter.toBase64(img);
        String base64b = imageConverter.toBase64(img);

        assertThat(base64a).isEqualTo(base64b);
    }

    @Test
    @DisplayName("DOIT retourner un type MIME non vide")
    void shouldReturnNonBlankMimeType() {
        assertThat(imageConverter.mimeType()).isNotBlank();
    }

    @Test
    @DisplayName("DOIT retourner un type MIME image valide")
    void shouldReturnValidImageMimeType() {
        assertThat(imageConverter.mimeType()).startsWith("image/");
    }

    @Test
    @DisplayName("DOIT lever une exception quand l'image est null")
    void shouldThrowExceptionWhenImageIsNull() {
        assertThatThrownBy(() -> imageConverter.toBase64(null))
            .isInstanceOf(Exception.class);
    }
}
