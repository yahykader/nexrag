package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Date;

/**
 * Réponse pour les opérations de suppression.
 *
 * Principe SRP : unique responsabilité → transporter les données de réponse DELETE.
 * Immutable via @Value (clean code).
 */
@Value
@Builder
public class DeleteResponse {
    Boolean success;
    Integer deletedCount;
    String  embeddingId;
    String  batchId;
    String  type;
    String  message;
    Date    timestamp;
}