package com.exemple.nexrag.service.rag.ingestion.security;

import com.exemple.nexrag.exception.VirusDetectedException;
import com.exemple.nexrag.exception.AntivirusScanException;
import com.exemple.nexrag.dto.ScanResult;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Garde antivirus pour les fichiers entrants.
 *
 * Principe SRP : unique responsabilité → vérifier qu'un fichier est sain
 *                avant ingestion. Extrait la logique inline de l'orchestrateur.
 * Clean code   : supprime la création/suppression de fichier temporaire inutile
 *                — {@link AntivirusScanner#scanBytes} existe déjà pour scanner
 *                des données en mémoire.
 *
 * @author RAG Team
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AntivirusGuard {

    private final AntivirusScanner antivirusScanner;
    private final RAGMetrics       ragMetrics;

    @Value("${antivirus.enabled:false}")
    private boolean enabled;

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    /**
     * Scanne le contenu d'un fichier uploadé.
     *
     * <p>Si le scan est désactivé, retourne immédiatement sans erreur.
     * Si un virus est détecté, lève {@link VirusDetectedException}.
     *
     * @param file     fichier à scanner
     * @throws VirusDetectedException  si un virus est détecté
     * @throws AntivirusScanException  si le scan échoue techniquement
     * @throws IOException             si la lecture des bytes échoue
     */
    public void assertClean(MultipartFile file) throws IOException {
        if (!enabled) {
                log.debug("🔓 Antivirus désactivé : {}", file.getOriginalFilename());
                return;
            }

            String filename = file.getOriginalFilename();
            log.debug("🦠 Scan antivirus : {}", filename);

            try {
                ScanResult result = antivirusScanner.scanBytes(file.getBytes(), filename);

                if (!result.isClean()) {
                    log.error("🚨 Virus détecté : {} — {}", filename, result.getVirusName());
                    ragMetrics.recordVirusDetected(result.getVirusName());
                    throw new VirusDetectedException("Virus détecté : " + result.getVirusName());
                }

                log.debug("✅ Fichier sain : {}", filename);

            } catch (VirusDetectedException e) {
                throw e; // ← toujours propager les vrais virus détectés

            } catch (AntivirusScanException e) {
                // ✅ Fail-open : timeout ou erreur technique ClamAV
                // → autoriser le fichier plutôt que bloquer toute l'ingestion
                log.warn("⚠️ [Antivirus] Scan échoué pour {} — fichier autorisé (fail-open) : {}",
                    filename, e.getMessage());
            }
    }
}