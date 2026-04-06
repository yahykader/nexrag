package com.exemple.nexrag.validation;

import com.exemple.nexrag.constant.VoiceConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Spec unitaire de {@link AudioFileValidator}.
 *
 * SRP : valide uniquement les contraintes de taille et de présence sur les fichiers audio.
 * DIP : aucune dépendance externe ; instanciation directe sans Spring.
 */
@DisplayName("Spec : AudioFileValidator — Validation des fichiers audio entrants")
class AudioFileValidatorSpec {

    private AudioFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AudioFileValidator();
    }

    // -------------------------------------------------------------------------
    // AC-22.10 — Fichier null → IllegalArgumentException contenant "vide"
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT rejeter un fichier audio null avec un message contenant 'vide'")
    void devraitRejeterFichierAudioNull() {
        assertThatThrownBy(() -> validator.validate(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vide");
    }

    // -------------------------------------------------------------------------
    // AC-22.10 — Fichier vide (0 byte) → IllegalArgumentException contenant "vide"
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT rejeter un fichier audio vide (0 byte) avec un message contenant 'vide'")
    void devraitRejeterFichierAudioVide() {
        MockMultipartFile file = new MockMultipartFile("audio", "empty.wav", "audio/wav", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vide");
    }

    // -------------------------------------------------------------------------
    // AC-22.10 — Taille > 25 MB → IllegalArgumentException contenant "25 MB"
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT rejeter un fichier audio dépassant la limite de 25 MB")
    void devraitRejeterFichierAudioDepassantLimite25Mo() {
        MockMultipartFile oversized = new MockMultipartFile("audio", "big.wav", "audio/wav", new byte[3]) {
            @Override
            public long getSize() {
                return VoiceConstants.MAX_AUDIO_SIZE_BYTES + 1;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }
        };

        assertThatThrownBy(() -> validator.validate(oversized))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("25 MB");
    }

    // -------------------------------------------------------------------------
    // AC-22.10 — Fichier valide en dessous de la limite → aucune exception
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT accepter un fichier audio valide en dessous de la limite de 25 MB")
    void devraitAccepterFichierAudioValideEnDessousLimite() {
        MockMultipartFile file = new MockMultipartFile(
            "audio", "sample.wav", "audio/wav", new byte[]{0x52, 0x49, 0x46, 0x46}
        );

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }
}
