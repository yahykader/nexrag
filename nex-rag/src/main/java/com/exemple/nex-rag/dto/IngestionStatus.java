package com.exemple.nexrag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Date;

/**
 * Détail d'une ingestion active — immuable.
 *
 * Clean code : tous les champs sont final.
 *              Les fabriques statiques remplacent le constructeur direct.
 *              {@code toBuilder = true} permet de copier l'état existant
 *              sans réécrire tous les champs dans {@code withStrategy()}
 *              et {@code completed()}.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class IngestionStatus {

    private final String  batchId;
    private final String  filename;
    private final String  strategy;
    private final Date    startTime;
    private final boolean completed;
    private final boolean success;
    private final long    duration;

    // -------------------------------------------------------------------------
    // Accesseurs style record — compatibles avec les consommateurs
    // -------------------------------------------------------------------------

    public String  batchId()   { return batchId;   }
    public String  filename()  { return filename;  }
    public String  strategy()  { return strategy;  }
    public Date    startTime() { return startTime; }
    public boolean completed() { return completed; }
    public boolean success()   { return success;   }
    public long    duration()  { return duration;  }

    // -------------------------------------------------------------------------
    // Fabriques statiques
    // -------------------------------------------------------------------------

    /** Crée un statut initial au démarrage de l'ingestion. */
    public static IngestionStatus started(String batchId, String filename) {
        return IngestionStatus.builder()
            .batchId(batchId)
            .filename(filename)
            .startTime(new Date())
            .completed(false)
            .success(false)
            .duration(0)
            .build();
    }

    /** Retourne une nouvelle instance avec la stratégie sélectionnée. */
    public IngestionStatus withStrategy(String strategyName) {
        return this.toBuilder()
            .strategy(strategyName)
            .build();
    }

    /** Retourne une nouvelle instance marquée comme terminée. */
    public IngestionStatus completed(boolean isSuccess, long durationMs) {
        return this.toBuilder()
            .completed(true)
            .success(isSuccess)
            .duration(durationMs)
            .build();
    }
}