package com.exemple.nexrag.validation;

import com.exemple.nexrag.constant.FileSizeConstants;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * Validateur de fichiers entrants.
 *
 * Principe SRP : unique responsabilité → valider les contraintes sur les fichiers.
 * Clean code   : élimine la méthode {@code validateFile()} inline dans le controller.
 */
@Component
public class FileValidator {

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
        "exe", "bat", "cmd", "msi", "com", "scr", "vbs", "ps1", "sh"
    );

    /**
     * Valide un fichier unique.
     *
     * @throws IllegalArgumentException si le fichier est invalide
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide ou absent");
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("Nom de fichier absent");
        }
        if (file.getSize() > FileSizeConstants.MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(String.format(
                "Fichier trop volumineux : %d MB (max : %d MB)",
                file.getSize() / 1_000_000,
                FileSizeConstants.MAX_FILE_SIZE_MB
            ));
        }

        String extension = getExtension(file.getOriginalFilename());
        if (BLOCKED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException(
                "Impossible d'uploader ce fichier — le type ."
                + extension + " n'est pas autorisé"
            );
        }
    }

    /**
     * Valide une liste de fichiers.
     *
     * @throws IllegalArgumentException si la liste est vide ou null
     */
    public void validateBatch(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier fourni");
        }
        files.forEach(this::validate);
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}