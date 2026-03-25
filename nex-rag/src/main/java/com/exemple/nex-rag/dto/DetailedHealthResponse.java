package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * Rapport de santé détaillé du service d'ingestion.
 *
 * Clean code : @Builder pour la construction, accesseurs sans préfixe
 *              pour rester compatibles avec les consommateurs existants.
 */
@Getter
@Builder
@AllArgsConstructor
public class DetailedHealthResponse {

    private final String  status;
    private final int     strategies;
    private final int     activeIngestions;
    private final int     trackerBatches;
    private final boolean redisHealthy;
    private final boolean antivirusHealthy;
    private final boolean antivirusEnabled;

    // -------------------------------------------------------------------------
    // Accesseurs style record — compatibles avec les consommateurs
    // -------------------------------------------------------------------------

    public String  status()           { return status;           }
    public int     strategies()       { return strategies;        }
    public int     activeIngestions() { return activeIngestions;  }
    public int     trackerBatches()   { return trackerBatches;    }
    public boolean redisHealthy()     { return redisHealthy;      }
    public boolean antivirusHealthy() { return antivirusHealthy;  }
    public boolean antivirusEnabled() { return antivirusEnabled;  }

    // -------------------------------------------------------------------------
    // Méthode métier
    // -------------------------------------------------------------------------

    /**
     * @return {@code true} si le statut est {@code "HEALTHY"}
     */
    public boolean isHealthy() {
        return "HEALTHY".equals(status);
    }
}