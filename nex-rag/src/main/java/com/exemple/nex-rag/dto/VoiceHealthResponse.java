package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Réponse pour le health check Voice.
 */
@Value
@Builder
public class VoiceHealthResponse {
    String  status;
    Boolean whisperAvailable;
}