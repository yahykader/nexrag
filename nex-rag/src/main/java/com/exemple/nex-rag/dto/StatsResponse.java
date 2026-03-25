package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * Statistiques courantes du service d'ingestion.
 *
 * Clean code : @Builder pour la construction, accesseurs sans préfixe
 *              pour rester compatibles avec les consommateurs existants.
 */
@Getter
@Builder
@AllArgsConstructor
public class StatsResponse {

    private final int strategiesCount;
    private final int activeIngestions;
    private final int trackerBatches;
    private final int trackerEmbeddings;
    private final int filesInProgress;

    // -------------------------------------------------------------------------
    // Accesseurs style record — compatibles avec les consommateurs
    // -------------------------------------------------------------------------

    public int strategiesCount()  { return strategiesCount;  }
    public int activeIngestions() { return activeIngestions; }
    public int trackerBatches()   { return trackerBatches;   }
    public int trackerEmbeddings(){ return trackerEmbeddings;}
    public int filesInProgress()  { return filesInProgress;  }
}