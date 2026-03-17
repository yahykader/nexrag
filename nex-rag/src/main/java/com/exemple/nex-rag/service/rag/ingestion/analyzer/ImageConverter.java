package com.exemple.nexrag.service.rag.ingestion.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Conversion d'images vers Base64 pour les APIs Vision.
 *
 * Principe SRP : unique responsabilité → encoder une {@link BufferedImage} en Base64 PNG.
 * Clean code   : extrait {@code convertImageToBase64()} hors de {@link VisionAnalyzer}.
 */
@Slf4j
@Component
public class ImageConverter {

    private static final String FORMAT = "png";

    /**
     * Convertit une image en chaîne Base64 PNG.
     *
     * @param image image à convertir
     * @return Base64 de l'image
     * @throws IOException si la conversion échoue
     */
    public String toBase64(BufferedImage image) throws IOException {
        if (image == null) throw new IllegalArgumentException("Image ne peut pas être null");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, FORMAT, baos)) {
                throw new IOException("Impossible d'écrire l'image en " + FORMAT);
            }

            byte[] bytes  = baos.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(bytes);

            log.debug("🔄 [ImageConverter] {}x{} → {} bytes → {} chars base64",
                image.getWidth(), image.getHeight(), bytes.length, base64.length());

            return base64;
        }
    }

    /** Retourne le type MIME correspondant au format de sortie. */
    public String mimeType() {
        return "image/" + FORMAT;
    }
}