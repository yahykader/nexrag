package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Réponse pour la vérification de doublons.
 *
 * Principe SRP : unique responsabilité → transporter les données de vérification doublon.
 */
@Value
@Builder
public class DuplicateCheckResponse {
    Boolean isDuplicate;
    String  filename;
    String  existingBatchId;
    String  message;
}