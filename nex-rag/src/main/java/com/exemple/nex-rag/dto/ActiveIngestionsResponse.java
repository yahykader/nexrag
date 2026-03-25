package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Réponse pour la liste des ingestions actives.
 */
@Value
@Builder
public class ActiveIngestionsResponse {
    Integer                   count;
    List<IngestionStatus> ingestions;
}