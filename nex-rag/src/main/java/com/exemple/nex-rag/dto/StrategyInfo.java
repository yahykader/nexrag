package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * Informations descriptives d'une stratégie d'ingestion.
 *
 * Clean code : @Builder pour la construction, accesseurs sans préfixe
 *              pour rester compatibles avec les consommateurs existants.
 */
@Getter
@Builder
@AllArgsConstructor
public class StrategyInfo {

    private final String name;
    private final int    priority;
    private final String className;

    // -------------------------------------------------------------------------
    // Accesseurs style record — compatibles avec les consommateurs
    // -------------------------------------------------------------------------

    public String name()      { return name;      }
    public int    priority()  { return priority;  }
    public String className() { return className; }
}