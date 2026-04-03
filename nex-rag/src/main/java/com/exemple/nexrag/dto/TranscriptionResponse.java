package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;
import lombok.AllArgsConstructor;

/**
 * Réponse pour une transcription audio réussie.
 *
 * Clean code : remplace {@code Map<String, Object>} non typé.
 */
@Value
@Builder
@AllArgsConstructor
public class TranscriptionResponse {
    boolean success;
    String  transcript;
    String  language;
    Long    audioSize;
    String  filename;
}