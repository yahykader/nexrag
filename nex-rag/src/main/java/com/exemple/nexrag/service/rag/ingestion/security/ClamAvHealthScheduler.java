package com.exemple.nexrag.service.rag.ingestion.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler de health check pour le daemon ClamAV.
 *
 * Principe SRP : unique responsabilité → vérifier périodiquement
 *                la disponibilité de ClamAV.
 * Clean code   : extrait le scheduling hors de {@link ClamAvConfig},
 *                qui n'avait pas à gérer un cycle de vie métier.
 *
 * Actif uniquement si {@code antivirus.enabled=true}
 * et {@code antivirus.health-check.enabled=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "antivirus",
    name   = {"enabled", "health-check.enabled"},
    havingValue = "true"
)
public class ClamAvHealthScheduler {

    private final AntivirusScanner antivirusScanner;

    /**
     * Vérifie la disponibilité de ClamAV à intervalle régulier.
     *
     * <p>L'intervalle est configuré via {@code antivirus.health-check.interval}
     * (défaut : 60 000 ms).
     */
    @Scheduled(fixedDelayString = "${antivirus.health-check.interval:60000}")
    public void checkHealth() {
        try {
            if (!antivirusScanner.isAvailable()) {
                log.warn("⚠️ [HEALTH CHECK] ClamAV est indisponible — " +
                         "les fichiers ne seront PAS scannés jusqu'au rétablissement");
                // TODO : déclencher une alerte monitoring (Prometheus, PagerDuty, etc.)
            } else {
                log.debug("✓ [HEALTH CHECK] ClamAV opérationnel");
            }
        } catch (Exception e) {
            log.error("❌ [HEALTH CHECK] Erreur lors de la vérification ClamAV", e);
        }
    }
}