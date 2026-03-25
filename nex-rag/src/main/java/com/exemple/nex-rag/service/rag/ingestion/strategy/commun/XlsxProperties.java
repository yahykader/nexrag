package com.exemple.nexrag.service.rag.ingestion.strategy.commun;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Propriétés de configuration de la stratégie XLSX.
 *
 * Principe DIP  : les services dépendent de cette abstraction,
 *                 pas de @Value éparpillés.
 * Clean code    : regroupe sous document.xlsx.* toutes les clés
 *                 anciennement réparties entre app.libreoffice.*,
 *                 app.pdf.* et document.xlsx.*.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "document.xlsx")
public class XlsxProperties {

    /** Nombre maximum d'images extraites par fichier. */
    @Positive
    private int maxImagesPerFile = 100;

    /** Nombre maximum de pages PDF analysées via Vision AI. */
    @Positive
    private int maxPdfPagesToAnalyze = 20;

    /** Évaluation des formules lors de la lecture des cellules. */
    private boolean evaluateFormulas = true;

    /** Détection des charts pour déclencher la voie LibreOffice. */
    private boolean detectCharts = true;

    private Libreoffice libreoffice = new Libreoffice();
    private Pdf         pdf         = new Pdf();

    // -------------------------------------------------------------------------

    @Getter @Setter
    public static class Libreoffice {

        /** Active ou désactive la conversion via LibreOffice. */
        private boolean enabled = true;

        /**
         * Chemin absolu vers l'exécutable soffice.
         * Laisser vide pour activer l'auto-détection par OS.
         */
        private String sofficePath = "";

        /** Timeout de conversion en secondes. */
        @Positive
        private int timeoutSeconds = 60;
    }

    // -------------------------------------------------------------------------

    @Getter @Setter
    public static class Pdf {

        /** Sauvegarde le PDF généré par LibreOffice sur disque. */
        private boolean saveGenerated = true;

        /** Répertoire de sauvegarde des PDFs générés. */
        @NotBlank
        private String generatedPdfDir = "uploads/generated-pdfs";

        /** Active l'analyse Vision AI sur les pages du PDF généré. */
        private boolean analyzeWithVision = true;

        /** Résolution de rendu des pages PDF en DPI. */
        @Positive
        private int renderDpi = 300;
    }
}