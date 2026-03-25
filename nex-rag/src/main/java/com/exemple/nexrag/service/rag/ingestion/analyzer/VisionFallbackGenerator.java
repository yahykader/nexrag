package com.exemple.nexrag.service.rag.ingestion.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * Générateur de descriptions de fallback lorsque Vision AI est indisponible.
 *
 * Principe SRP : unique responsabilité → produire une description basique
 *                sans appel API.
 * Clean code   : élimine la duplication des 5 méthodes fallback dans
 *                {@link VisionAnalyzer} — chacune appelait cette même logique.
 */
@Slf4j
@Component
public class VisionFallbackGenerator {

    private static final String UNAVAILABLE =
        "[Vision AI temporairement indisponible]";

    /**
     * Génère une description de fallback adaptée au type d'analyse demandé.
     *
     * @param image        image analysée (peut être null)
     * @param analysisType type d'analyse demandé
     * @param cause        exception ayant déclenché le fallback (pour le log)
     * @return description basique lisible par un humain
     */
    public String generate(BufferedImage image, AnalysisType analysisType, Exception cause) {
        log.warn("🔄 [VisionAI] Circuit Breaker OPEN — fallback={} cause={}",
            analysisType, cause.getClass().getSimpleName());

        if (image == null) {
            return UNAVAILABLE + " Impossible d'analyser l'image (null).";
        }

        int    w           = image.getWidth();
        int    h           = image.getHeight();
        String orientation = w > h ? "landscape" : w < h ? "portrait" : "square";

        return switch (analysisType) {
            case TEXT     -> String.format(
                "%s Impossible d'extraire le texte de l'image %s de %dx%d pixels. " +
                "Veuillez réessayer plus tard.", UNAVAILABLE, orientation, w, h);
            case OBJECTS  -> String.format(
                "%s Impossible de détecter les objets dans l'image %s de %dx%d pixels. " +
                "Veuillez réessayer plus tard.", UNAVAILABLE, orientation, w, h);
            case CHART    -> String.format(
                "%s Impossible d'analyser le graphique dans l'image %s de %dx%d pixels. " +
                "Veuillez réessayer plus tard.", UNAVAILABLE, orientation, w, h);
            case SCENE    -> String.format(
                "%s Impossible de décrire la scène dans l'image %s de %dx%d pixels. " +
                "Veuillez réessayer plus tard.", UNAVAILABLE, orientation, w, h);
            default       -> String.format(
                "%s Image %s de %dx%d pixels. " +
                "Service d'analyse en maintenance. Veuillez réessayer dans quelques minutes.",
                UNAVAILABLE, orientation, w, h);
        };
    }
}