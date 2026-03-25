package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Réponse pour la liste des stratégies disponibles.
 */
@Value
@Builder
public class StrategiesResponse {
    Integer            count;
    List<StrategyInfo> strategies;
}