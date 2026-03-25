package com.exemple.nexrag.service.rag.ingestion.analyzer;

import com.exemple.nexrag.service.rag.ingestion.analyzer.FilenameHandler;
import com.exemple.nexrag.service.rag.ingestion.analyzer.ImageStorageProperties;
import com.exemple.nexrag.service.rag.ingestion.analyzer.StorageStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service de sauvegarde d'images extraites sur disque.
 *
 * Principe SRP : unique responsabilité → persister une image sur le système de fichiers.
 *                La sanitisation des noms est dans {@link FilenameHandler}.
 *                Les statistiques de stockage sont dans {@link StorageStatsService}.
 * Clean code   : élimine le {@code @Value} — remplacé par {@link ImageStorageProperties}.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageSaver {

    private static final String DEFAULT_FORMAT = "png";

    private final ImageStorageProperties props;
    private final FilenameHandler        filenameHandler;

    // -------------------------------------------------------------------------
    // Sauvegarde
    // -------------------------------------------------------------------------

    /**
     * Sauvegarde une image en PNG dans le répertoire configuré.
     *
     * @param image     image à sauvegarder
     * @param imageName nom de base du fichier (sans extension)
     * @return chemin absolu du fichier sauvegardé
     * @throws IOException si la sauvegarde échoue
     */
    public String saveImage(BufferedImage image, String imageName) throws IOException {
        return saveImage(image, imageName, DEFAULT_FORMAT);
    }

    /**
     * Sauvegarde une image dans le format demandé.
     */
    public String saveImage(BufferedImage image, String imageName, String format)
            throws IOException {

        if (image     == null) throw new IllegalArgumentException("Image ne peut pas être null");
        if (imageName == null || imageName.isBlank())
            throw new IllegalArgumentException("Nom d'image ne peut pas être vide");

        ensureStorageDirectoryExists();

        String sanitized = filenameHandler.sanitize(imageName);
        Path   imagePath = resolveUniquePath(sanitized, format);
        File   imageFile = imagePath.toFile();

        if (!ImageIO.write(image, format, imageFile)) {
            throw new IOException("Impossible de sauvegarder l'image au format : " + format);
        }

        log.debug("💾 [ImageSaver] Sauvegardé : {} ({}x{} px, {} bytes)",
            imageFile.getName(), image.getWidth(), image.getHeight(), imageFile.length());

        return imageFile.getAbsolutePath();
    }

    public String getStoragePath() {
        return props.getStoragePath();
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    /**
     * Retourne un chemin unique — ajoute un timestamp si le fichier existe déjà.
     */
    private Path resolveUniquePath(String sanitized, String format) {
        String   filename = sanitized + "." + format.toLowerCase();
        Path     path     = Paths.get(props.getStoragePath(), filename);

        if (path.toFile().exists()) {
            filename = sanitized + "_" + filenameHandler.timestampSuffix() + "." + format.toLowerCase();
            path     = Paths.get(props.getStoragePath(), filename);
        }

        return path;
    }

    private void ensureStorageDirectoryExists() throws IOException {
        Path path = Paths.get(props.getStoragePath());

        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log.info("📁 [ImageSaver] Répertoire créé : {}", props.getStoragePath());
        }

        if (!Files.isWritable(path)) {
            throw new IOException("Répertoire non accessible en écriture : " + props.getStoragePath());
        }
    }
}