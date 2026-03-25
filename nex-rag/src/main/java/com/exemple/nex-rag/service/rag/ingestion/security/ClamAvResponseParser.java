package com.exemple.nexrag.service.rag.ingestion.security;

import com.exemple.nexrag.dto.ScanResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parseur de réponses ClamAV.
 *
 * Principe SRP : unique responsabilité → interpréter les réponses texte de ClamAV.
 * Clean code   : extrait {@code parseResponse()} et {@code extractVirusName()}
 *                hors de {@code AntivirusScanner}.
 *
 * Formats attendus :
 * <ul>
 *   <li>Propre   : {@code stream: OK}</li>
 *   <li>Infecté  : {@code stream: Eicar-Test-Signature FOUND}</li>
 *   <li>Erreur   : {@code stream: ERROR ...}</li>
 * </ul>
 */
@Slf4j
@Component
public class ClamAvResponseParser {

    private static final String TOKEN_OK       = "OK";
    private static final String TOKEN_FOUND    = "FOUND";
    private static final String TOKEN_ERROR    = "ERROR";
    private static final String UNKNOWN_VIRUS  = "Unknown";

    /**
     * Convertit la réponse brute de ClamAV en {@link ScanResult}.
     *
     * @param filename nom du fichier scanné (pour les logs et le résultat)
     * @param response réponse brute reçue du daemon
     * @return résultat typé du scan
     */
    public ScanResult parse(String filename, String response) {
        log.debug("📥 Réponse ClamAV : {}", response);

        if (response.contains(TOKEN_OK)) {
            log.info("✅ Fichier propre : {}", filename);
            return ScanResult.clean(filename);
        }

        if (response.contains(TOKEN_FOUND)) {
            String virusName = extractVirusName(response);
            log.warn("⚠️ Virus détecté : {} dans {}", virusName, filename);
            return ScanResult.infected(filename, virusName);
        }

        if (response.contains(TOKEN_ERROR)) {
            log.error("❌ Erreur ClamAV : {}", response);
            return ScanResult.error(filename, response);
        }

        log.warn("⚠️ Réponse ClamAV inconnue : {}", response);
        return ScanResult.error(filename, "Réponse inconnue : " + response);
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    /**
     * Extrait le nom du virus d'une réponse ClamAV.
     * Exemple : {@code "stream: Eicar-Test-Signature FOUND"} → {@code "Eicar-Test-Signature"}.
     */
    private String extractVirusName(String response) {
        String[] parts = response.split(":");
        if (parts.length < 2) return UNKNOWN_VIRUS;

        String virusPart  = parts[1].trim();
        int    foundIndex = virusPart.lastIndexOf(TOKEN_FOUND);

        return foundIndex > 0
            ? virusPart.substring(0, foundIndex).trim()
            : virusPart;
    }
}