package com.exemple.nexrag.validation;

import com.exemple.nexrag.constant.VoiceConstants;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validateur de fichiers audio entrants.
 *
 * Principe SRP : unique responsabilité → valider les contraintes sur les fichiers audio.
 * Clean code   : extrait la validation inline du controller.
 */
@Component
public class AudioFileValidator {

    /**
     * Valide un fichier audio.
     *
     * @param file fichier à valider
     * @throws IllegalArgumentException si le fichier est invalide
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier audio est vide");
        }
        if (file.getSize() > VoiceConstants.MAX_AUDIO_SIZE_BYTES) {
            throw new IllegalArgumentException(String.format(
                "Le fichier audio dépasse 25 MB (%d bytes reçus)",
                file.getSize()
            ));
        }
    }
}