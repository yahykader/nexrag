package com.exemple.nexrag.service.rag.voice;

import com.exemple.nexrag.config.WhisperProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Gestion du cycle de vie des fichiers audio temporaires.
 *
 * Principe SRP  : unique responsabilité → créer et supprimer les fichiers
 *                 temporaires nécessaires à l'API Whisper.
 * Clean code    : extrait {@code createTempAudioFile()} et {@code getFileExtension()}
 *                 hors de {@link WhisperService}.
 *                 Utilise {@link Files#write} (java.nio) au lieu de
 *                 Apache Commons {@code FileUtils.writeByteArrayToFile}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioTempFile {

    private final WhisperProperties props;

    /**
     * Crée un fichier temporaire contenant les bytes audio.
     *
     * @param audioBytes     contenu audio
     * @param originalFilename nom du fichier original (pour l'extension)
     * @return fichier temporaire prêt à être envoyé à l'API
     * @throws IOException si l'écriture échoue
     */
    public File create(byte[] audioBytes, String originalFilename) throws IOException {
        String extension = resolveExtension(originalFilename);
        String name      = "whisper_" + UUID.randomUUID() + extension;
        Path   path      = Path.of(System.getProperty("java.io.tmpdir"), name);

        Files.write(path, audioBytes);

        log.debug("📁 [Whisper] Fichier temp créé : {} ({} bytes)", name, audioBytes.length);
        return path.toFile();
    }

    /**
     * Supprime le fichier temporaire sans lever d'exception.
     */
    public void deleteSilently(File file) {
        if (file != null && file.exists()) {
            boolean deleted = file.delete();
            log.debug("🗑️ [Whisper] Fichier temp supprimé : {}", deleted);
        }
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    /**
     * Retourne l'extension du fichier, ou l'extension par défaut si absente.
     */
    private String resolveExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return props.getDefaultExtension();
        }
        int dot = filename.lastIndexOf('.');
        return (dot > 0 && dot < filename.length() - 1)
            ? filename.substring(dot)
            : props.getDefaultExtension();
    }
}