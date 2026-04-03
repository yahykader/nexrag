package com.exemple.nexrag.service.rag.controller;

import com.exemple.nexrag.config.TestWebConfig;
import com.exemple.nexrag.service.rag.ingestion.ratelimit.RateLimitService;
import com.exemple.nexrag.advice.VoiceExceptionHandler;
import com.exemple.nexrag.constant.VoiceConstants;
import com.exemple.nexrag.dto.TranscriptionResponse;
import com.exemple.nexrag.dto.VoiceHealthResponse;
import com.exemple.nexrag.service.rag.facade.VoiceFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spec : VoiceController — API de transcription audio
 *
 * Principe SRP : valide uniquement le routage HTTP, le passage du paramètre
 * language à VoiceFacade, et le mapping des exceptions via VoiceExceptionHandler.
 */
@DisplayName("Spec : VoiceController — API de transcription audio")
@WebMvcTest(VoiceController.class)
@Import({ VoiceExceptionHandler.class, TestWebConfig.class })
class VoiceControllerSpec {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VoiceFacade voiceFacade;


    private static final MockMultipartFile VALID_AUDIO =
            new MockMultipartFile("audio", "voice.webm", "audio/webm", "audio-data".getBytes());

    // =========================================================================
    // Transcription — US-4 / AC-1
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 avec la transcription pour un audio valide")
    void shouldReturn200ForSuccessfulTranscription() throws Exception {
        when(voiceFacade.transcribe(any(), any()))
                .thenReturn(TranscriptionResponse.builder()
                        .success(true)
                        .transcript("Bonjour le monde")
                        .build());

        mockMvc.perform(multipart("/api/v1/voice/transcribe")
                        .file(VALID_AUDIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.transcript").value("Bonjour le monde"));
    }

    // =========================================================================
    // Paramètre language explicite — US-4 / AC-2
    // =========================================================================

    @Test
    @DisplayName("DOIT transmettre le code langue 'en' à VoiceFacade quand fourni")
    void shouldForwardLanguageCodeToFacade() throws Exception {
        when(voiceFacade.transcribe(any(), eq("en")))
                .thenReturn(TranscriptionResponse.builder().success(true).transcript("Hello").build());

        mockMvc.perform(multipart("/api/v1/voice/transcribe")
                        .file(VALID_AUDIO)
                        .param("language", "en"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> langCaptor = ArgumentCaptor.forClass(String.class);
        verify(voiceFacade).transcribe(any(), langCaptor.capture());
        assertThat(langCaptor.getValue()).isEqualTo("en");
    }

    // =========================================================================
    // Langue par défaut — US-4 / AC-3
    // =========================================================================

    @Test
    @DisplayName("DOIT utiliser la langue par défaut quand le paramètre language est absent")
    void shouldUseDefaultLanguageWhenNotProvided() throws Exception {
        when(voiceFacade.transcribe(any(), eq(VoiceConstants.DEFAULT_LANGUAGE)))
                .thenReturn(TranscriptionResponse.builder().success(true).transcript("Texte").build());

        mockMvc.perform(multipart("/api/v1/voice/transcribe")
                        .file(VALID_AUDIO))
                .andExpect(status().isOk());

        ArgumentCaptor<String> langCaptor = ArgumentCaptor.forClass(String.class);
        verify(voiceFacade).transcribe(any(), langCaptor.capture());
        assertThat(langCaptor.getValue()).isEqualTo(VoiceConstants.DEFAULT_LANGUAGE);
    }

    // =========================================================================
    // Health — US-4 / AC-4
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 pour le health check du service de transcription")
    void shouldReturn200ForHealthEndpoint() throws Exception {
        when(voiceFacade.health())
                .thenReturn(VoiceHealthResponse.builder().whisperAvailable(true).build());

        mockMvc.perform(get("/api/v1/voice/health"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // @ControllerAdvice — exception mapping (US-4 / edge cases)
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 400 quand VoiceFacade lève une IllegalArgumentException")
    void shouldReturn400WhenIllegalArgumentExceptionThrown() throws Exception {
        when(voiceFacade.transcribe(any(), any()))
                .thenThrow(new IllegalArgumentException("Fichier audio invalide"));

        mockMvc.perform(multipart("/api/v1/voice/transcribe")
                        .file(VALID_AUDIO))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DOIT retourner 400 quand le paramètre audio est absent")
    void shouldReturn400WhenAudioFileMissing() throws Exception {
        mockMvc.perform(multipart("/api/v1/voice/transcribe"))
                .andExpect(status().isBadRequest());
    }
}
