package com.exemple.nexrag.service.rag.ingestion.analyzer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec : VisionFallbackGenerator — Génération de descriptions de substitution.
 */
@DisplayName("Spec : VisionFallbackGenerator — Texte de substitution quand Vision AI est indisponible")
class VisionFallbackGeneratorSpec {

    private final VisionFallbackGenerator generator = new VisionFallbackGenerator();
    private final Exception cause = new RuntimeException("Circuit Breaker OPEN");

    private BufferedImage imagePortrait() {
        return new BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB);
    }

    private BufferedImage imageLandscape() {
        return new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    @DisplayName("DOIT retourner un texte non vide pour une image valide (type DEFAULT)")
    void shouldReturnNonEmptyTextForValidImage() {
        String result = generator.generate(imagePortrait(), AnalysisType.DEFAULT, cause);

        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("DOIT mentionner l'indisponibilité dans le texte de fallback")
    void shouldMentionUnavailabilityInFallback() {
        String result = generator.generate(imagePortrait(), AnalysisType.DEFAULT, cause);

        assertThat(result).contains("[Vision AI temporairement indisponible]");
    }

    @Test
    @DisplayName("DOIT retourner un texte spécifique pour le type TEXT")
    void shouldReturnSpecificTextForTextType() {
        String result = generator.generate(imagePortrait(), AnalysisType.TEXT, cause);

        assertThat(result).contains("texte");
    }

    @Test
    @DisplayName("DOIT retourner un texte spécifique pour le type OBJECTS")
    void shouldReturnSpecificTextForObjectsType() {
        String result = generator.generate(imagePortrait(), AnalysisType.OBJECTS, cause);

        assertThat(result).contains("objets");
    }

    @Test
    @DisplayName("DOIT retourner un texte spécifique pour le type CHART")
    void shouldReturnSpecificTextForChartType() {
        String result = generator.generate(imageLandscape(), AnalysisType.CHART, cause);

        assertThat(result).contains("graphique");
    }

    @Test
    @DisplayName("DOIT inclure les dimensions de l'image dans le message de fallback")
    void shouldIncludeImageDimensionsInFallback() {
        String result = generator.generate(imageLandscape(), AnalysisType.DEFAULT, cause);

        assertThat(result).contains("200").contains("100");
    }

    @Test
    @DisplayName("DOIT gérer une image null sans lever d'exception")
    void shouldHandleNullImageWithoutException() {
        String result = generator.generate(null, AnalysisType.DEFAULT, cause);

        assertThat(result).isNotBlank().contains("[Vision AI temporairement indisponible]");
    }

    @Test
    @DisplayName("DOIT mentionner l'orientation landscape pour une image large")
    void shouldMentionLandscapeOrientationForWideImage() {
        String result = generator.generate(imageLandscape(), AnalysisType.DEFAULT, cause);

        assertThat(result).contains("landscape");
    }

    @Test
    @DisplayName("DOIT mentionner l'orientation portrait pour une image haute")
    void shouldMentionPortraitOrientationForTallImage() {
        String result = generator.generate(imagePortrait(), AnalysisType.DEFAULT, cause);

        assertThat(result).contains("portrait");
    }
}
