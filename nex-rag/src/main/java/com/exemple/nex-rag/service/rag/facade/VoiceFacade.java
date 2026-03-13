package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.dto.TranscriptionResponse;
import com.exemple.nexrag.dto.VoiceHealthResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Facade exposée au controller Voice.
 *
 * Principe ISP : interface fine → le controller ne dépend que de ce contrat.
 * Principe DIP : le controller dépend de cette abstraction, pas de {@code WhisperService}.
 *
 * @author ayahyaoui
 * @version 1.0
 */
public interface VoiceFacade {

    /**
     * Transcrit un fichier audio dans la langue donnée.
     *
     * @param audioFile fichier audio à transcrire
     * @param language  code langue (ex : "fr", "en")
     * @return réponse avec la transcription
     */
    TranscriptionResponse transcribe(MultipartFile audioFile, String language);

    /**
     * Retourne l'état de santé du service de transcription.
     *
     * @return état du service Whisper
     */
    VoiceHealthResponse health();
}