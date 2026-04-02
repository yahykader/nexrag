package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.dto.TranscriptionResponse;
import com.exemple.nexrag.dto.VoiceHealthResponse;
import com.exemple.nexrag.service.rag.voice.WhisperService;
import com.exemple.nexrag.validation.AudioFileValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec : VoiceFacadeImpl — orchestration de la transcription audio.
 *
 * Principe SRP : chaque méthode de test couvre un chemin de transcription distinct.
 * Principe DIP : WhisperService et AudioFileValidator sont injectés via @Mock.
 */
@DisplayName("Spec : VoiceFacadeImpl — Facade de transcription vocale")
@ExtendWith(MockitoExtension.class)
class VoiceFacadeImplSpec {

    @Mock private WhisperService    whisperService;
    @Mock private AudioFileValidator audioFileValidator;

    @InjectMocks private VoiceFacadeImpl facade;

    // -------------------------------------------------------------------------
    // AC-voice-happy — Audio valide → TranscriptionResponse success avec texte trimmé
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner TranscriptionResponse success avec transcription non vide pour un audio valide")
    void shouldReturnSuccessTranscriptionResponseForValidAudio() throws Exception {
        var audioFile = new MockMultipartFile(
            "audio", "discours.mp3", "audio/mpeg", "audio-bytes".getBytes()
        );
        when(whisperService.transcribeAudio(any(byte[].class), any(), any()))
            .thenReturn("  Bonjour le monde  ");

        TranscriptionResponse response = facade.transcribe(audioFile, "fr");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTranscript()).isEqualTo("Bonjour le monde");
        assertThat(response.getLanguage()).isEqualTo("fr");
        assertThat(response.getFilename()).isEqualTo("discours.mp3");
        verify(audioFileValidator).validate(audioFile);
        verify(whisperService).transcribeAudio(any(byte[].class), eq("discours.mp3"), eq("fr"));
    }

    // -------------------------------------------------------------------------
    // AC-voice-invalid — Audio invalide → exception de validation propagée
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT propager l'IllegalArgumentException du validateur quand le fichier audio est invalide")
    void shouldPropagateIllegalArgumentExceptionWhenAudioValidationFails() {
        var audioFile = new MockMultipartFile(
            "audio", "vide.mp3", "audio/mpeg", new byte[0]
        );
        doThrow(new IllegalArgumentException("Fichier audio invalide ou vide"))
            .when(audioFileValidator).validate(any());

        assertThatThrownBy(() -> facade.transcribe(audioFile, "fr"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Fichier audio invalide ou vide");
    }

    // -------------------------------------------------------------------------
    // AC-voice-unavailable — Health check → état de disponibilité Whisper
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner VoiceHealthResponse avec whisperAvailable=true quand Whisper est disponible")
    void shouldReturnHealthyStatusWhenWhisperServiceIsAvailable() {
        when(whisperService.isAvailable()).thenReturn(true);

        VoiceHealthResponse health = facade.health();

        assertThat(health.getStatus()).isEqualTo("ok");
        assertThat(health.getWhisperAvailable()).isTrue();
    }
}
