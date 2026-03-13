package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Détail d'une stratégie d'ingestion.
 * Remplace {@code Map<String, Object>} non typé.
 */
@Value
@Builder
public class StrategyInfo {
    String name;
    int    priority;
    String className;
}