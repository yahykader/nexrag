package com.exemple.nexrag.service.rag.controller;

import com.exemple.nexrag.constant.VoiceConstants;
import com.exemple.nexrag.dto.TranscriptionResponse;
import com.exemple.nexrag.dto.VoiceHealthResponse;
import com.exemple.nexrag.service.rag.facade.VoiceFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller REST pour la transcription audio (Speech-to-Text).
 *
 * Principe SRP : unique responsabilité → router les requêtes HTTP.
 *                Zéro logique métier ici — tout délégué à {@link VoiceFacade}.
 * Principe DIP : dépend de l'abstraction VoiceFacade, pas de WhisperService.
 * Clean code   : zéro try/catch, zéro Map non typé, zéro magic number.
 *
 * @author RAG Team
 * @version 2.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
@Tag(name = "Google Speech-to-Text", description = "API Google Speech-to-Text")
public class VoiceController {

    private final VoiceFacade voiceFacade;

    // =========================================================================
    // TRANSCRIPTION
    // =========================================================================

    @PostMapping("/transcribe")
    @Operation(
        summary     = "Transcrire un fichier audio",
        description = "Formats acceptés : webm, mp3, wav. Taille max : 25 MB."
    )
    public ResponseEntity<TranscriptionResponse> transcribe(
            @Parameter(description = "Fichier audio à transcrire")
            @RequestParam("audio") MultipartFile audioFile,
            @Parameter(description = "Code langue (fr, en, es…)")
            @RequestParam(value = "language", required = false,
                          defaultValue = VoiceConstants.DEFAULT_LANGUAGE) String language) {

        return ResponseEntity.ok(voiceFacade.transcribe(audioFile, language));
    }

    // =========================================================================
    // HEALTH
    // =========================================================================

    @GetMapping("/health")
    @Operation(summary = "Health check du service de transcription")
    public ResponseEntity<VoiceHealthResponse> health() {
        return ResponseEntity.ok(voiceFacade.health());
    }
}