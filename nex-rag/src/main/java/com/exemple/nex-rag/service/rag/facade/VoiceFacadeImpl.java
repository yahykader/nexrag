package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.dto.TranscriptionResponse;
import com.exemple.nexrag.dto.VoiceHealthResponse;
import com.exemple.nexrag.validation.AudioFileValidator;
import com.exemple.nexrag.service.rag.voice.WhisperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Implémentation de la facade Voice.
 *
 * Principe SRP : unique responsabilité → orchestrer la transcription audio.
 * Principe DIP : dépend de l'abstraction {@link WhisperService}.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceFacadeImpl implements VoiceFacade {

    private final WhisperService    whisperService;
    private final AudioFileValidator audioFileValidator;

    // -------------------------------------------------------------------------
    // VoiceFacade API
    // -------------------------------------------------------------------------

    @Override
    public TranscriptionResponse transcribe(MultipartFile audioFile, String language) {
        audioFileValidator.validate(audioFile);

        logAudioReceived(audioFile, language);

        String transcript = executeTranscription(audioFile, language);

        log.info("✅ [Voice] Transcription réussie — {} caractères", transcript.length());

        return TranscriptionResponse.builder()
            .success(true)
            .transcript(transcript)
            .language(language)
            .audioSize(audioFile.getSize())
            .filename(audioFile.getOriginalFilename())
            .build();
    }

    @Override
    public VoiceHealthResponse health() {
        return VoiceHealthResponse.builder()
            .status("ok")
            .whisperAvailable(whisperService.isAvailable())
            .build();
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private String executeTranscription(MultipartFile audioFile, String language) {
        try {
            return whisperService.transcribeAudio(
                audioFile.getBytes(),
                audioFile.getOriginalFilename(),
                language
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                "Impossible de lire le fichier audio : " + e.getMessage(), e
            );
        }
    }

    /**
     * Log groupé — un seul événement métier, un seul log structuré.
     * Clean code : remplace les 4 appels {@code log.info()} successifs.
     */
    private void logAudioReceived(MultipartFile audioFile, String language) {
        log.info("📥 [Voice] Fichier reçu — nom={}, taille={} bytes, type={}, langue={}",
            audioFile.getOriginalFilename(),
            audioFile.getSize(),
            audioFile.getContentType(),
            language
        );
    }
}