package com.exemple.nexrag.service.rag.ingestion.analyzer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Spec : VisionAnalyzer — Analyse d'images via Vision AI.
 */
@DisplayName("Spec : VisionAnalyzer — Analyse d'images avec fallback sur indisponibilité Vision AI")
@ExtendWith(MockitoExtension.class)
class VisionAnalyzerSpec {

    @Mock private ChatLanguageModel      visionModel;
    @Mock private ImageConverter         imageConverter;
    @Mock private VisionFallbackGenerator fallbackGenerator;
    @Mock private VisionProperties       props;

    @InjectMocks
    private VisionAnalyzer visionAnalyzer;

    private static final String DESCRIPTION = "Une image de test avec un fond blanc.";
    private static final String PROMPT      = "Décris cette image.";

    private BufferedImage imageValide() {
        return new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    }

    private Response<AiMessage> reponseVision(String texte) {
        return Response.from(AiMessage.from(texte));
    }

    // -------------------------------------------------------------------------
    // Analyse réussie
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner la description quand Vision AI répond correctement")
    void shouldReturnDescriptionWhenVisionAiRespondsCorrectly() throws IOException {
        when(props.getPrompt()).thenReturn(PROMPT);
        when(imageConverter.toBase64(any())).thenReturn("base64data");
        when(imageConverter.mimeType()).thenReturn("image/png");
        when(visionModel.generate(any(UserMessage.class))).thenReturn(reponseVision(DESCRIPTION));

        String result = visionAnalyzer.analyzeImage(imageValide());

        assertThat(result).isEqualTo(DESCRIPTION);
    }

    @Test
    @DisplayName("DOIT appeler imageConverter pour encoder l'image en Base64")
    void shouldCallImageConverterToEncodeImage() throws IOException {
        when(props.getPrompt()).thenReturn(PROMPT);
        when(imageConverter.toBase64(any())).thenReturn("base64data");
        when(imageConverter.mimeType()).thenReturn("image/png");
        when(visionModel.generate(any(UserMessage.class))).thenReturn(reponseVision(DESCRIPTION));

        visionAnalyzer.analyzeImage(imageValide());

        verify(imageConverter).toBase64(any(BufferedImage.class));
    }

    @Test
    @DisplayName("DOIT appeler visionModel.generate avec un UserMessage")
    void shouldCallVisionModelWithUserMessage() throws IOException {
        when(props.getPrompt()).thenReturn(PROMPT);
        when(imageConverter.toBase64(any())).thenReturn("base64data");
        when(imageConverter.mimeType()).thenReturn("image/png");
        when(visionModel.generate(any(UserMessage.class))).thenReturn(reponseVision(DESCRIPTION));

        visionAnalyzer.analyzeImage(imageValide());

        verify(visionModel).generate(any(UserMessage.class));
    }

    // -------------------------------------------------------------------------
    // Analyse avec prompt personnalisé
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT utiliser le prompt personnalisé quand fourni")
    void shouldUseCustomPromptWhenProvided() throws IOException {
        when(imageConverter.toBase64(any())).thenReturn("base64data");
        when(imageConverter.mimeType()).thenReturn("image/png");
        when(visionModel.generate(any(UserMessage.class))).thenReturn(reponseVision(DESCRIPTION));

        String result = visionAnalyzer.analyzeImage(imageValide(), "Mon prompt personnalisé");

        assertThat(result).isEqualTo(DESCRIPTION);
    }

    // -------------------------------------------------------------------------
    // Validation des entrées
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT lever IllegalArgumentException quand l'image est null")
    void shouldThrowIllegalArgumentWhenImageIsNull() {
        assertThatThrownBy(() -> visionAnalyzer.analyzeImage(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DOIT lever IOException quand Vision AI retourne une réponse null")
    void shouldThrowIOExceptionWhenVisionAiReturnsNull() throws IOException {
        when(props.getPrompt()).thenReturn(PROMPT);
        when(imageConverter.toBase64(any())).thenReturn("base64data");
        when(imageConverter.mimeType()).thenReturn("image/png");
        when(visionModel.generate(any(UserMessage.class))).thenReturn(null);

        assertThatThrownBy(() -> visionAnalyzer.analyzeImage(imageValide()))
            .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("DOIT lever IOException quand Vision AI retourne une description vide")
    void shouldThrowIOExceptionWhenVisionAiReturnsBlankText() throws IOException {
        when(props.getPrompt()).thenReturn(PROMPT);
        when(imageConverter.toBase64(any())).thenReturn("base64data");
        when(imageConverter.mimeType()).thenReturn("image/png");
        when(visionModel.generate(any(UserMessage.class))).thenReturn(reponseVision("   "));

        assertThatThrownBy(() -> visionAnalyzer.analyzeImage(imageValide()))
            .isInstanceOf(IOException.class);
    }

    // -------------------------------------------------------------------------
    // Méthodes spécialisées (extractText, detectObjects, etc.)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT extraire le texte via AnalysisType.TEXT")
    void shouldExtractTextViaTextAnalysisType() throws IOException {
        when(imageConverter.toBase64(any())).thenReturn("base64data");
        when(imageConverter.mimeType()).thenReturn("image/png");
        when(visionModel.generate(any(UserMessage.class))).thenReturn(reponseVision("Texte extrait"));

        String result = visionAnalyzer.extractText(imageValide());

        assertThat(result).isEqualTo("Texte extrait");
    }

    @Test
    @DisplayName("DOIT détecter les objets via AnalysisType.OBJECTS")
    void shouldDetectObjectsViaObjectsAnalysisType() throws IOException {
        when(imageConverter.toBase64(any())).thenReturn("base64data");
        when(imageConverter.mimeType()).thenReturn("image/png");
        when(visionModel.generate(any(UserMessage.class))).thenReturn(reponseVision("Objet détecté"));

        String result = visionAnalyzer.detectObjects(imageValide());

        assertThat(result).isEqualTo("Objet détecté");
    }

    // -------------------------------------------------------------------------
    // Analyse batch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner une liste vide pour une entrée null")
    void shouldReturnEmptyListForNullInput() throws IOException {
        assertThat(visionAnalyzer.analyzeImages(null)).isEmpty();
    }

    @Test
    @DisplayName("DOIT retourner une liste vide pour une entrée vide")
    void shouldReturnEmptyListForEmptyInput() throws IOException {
        assertThat(visionAnalyzer.analyzeImages(List.of())).isEmpty();
    }

    @Test
    @DisplayName("DOIT retourner un résultat par image dans le batch")
    void shouldReturnOneResultPerImageInBatch() throws IOException {
        when(props.getPrompt()).thenReturn(PROMPT);
        when(imageConverter.toBase64(any())).thenReturn("base64data");
        when(imageConverter.mimeType()).thenReturn("image/png");
        when(visionModel.generate(any(UserMessage.class))).thenReturn(reponseVision(DESCRIPTION));

        List<String> results = visionAnalyzer.analyzeImages(
            List.of(imageValide(), imageValide(), imageValide())
        );

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(r -> r.equals(DESCRIPTION));
    }

    @Test
    @DisplayName("DOIT continuer le traitement batch si une image lève une exception")
    void shouldContinueBatchProcessingWhenOneImageFails() throws IOException {
        when(props.getPrompt()).thenReturn(PROMPT);
        when(imageConverter.toBase64(any())).thenThrow(new IOException("Erreur encodage"));

        List<String> results = visionAnalyzer.analyzeImages(
            List.of(imageValide(), imageValide())
        );

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.startsWith("[Erreur analyse :"));
    }

    // -------------------------------------------------------------------------
    // Validation d'image (isValidImage)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner false pour une image null")
    void shouldReturnFalseForNullImage() {
        assertThat(visionAnalyzer.isValidImage(null)).isFalse();
    }

    @Test
    @DisplayName("DOIT retourner false pour une image trop petite (< 10x10)")
    void shouldReturnFalseForTooSmallImage() {
        assertThat(visionAnalyzer.isValidImage(new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB))).isFalse();
    }

    @Test
    @DisplayName("DOIT retourner false pour une image trop grande (> 4096 px)")
    void shouldReturnFalseForImageExceeding4096() {
        assertThat(visionAnalyzer.isValidImage(new BufferedImage(4097, 100, BufferedImage.TYPE_INT_RGB))).isFalse();
    }

    @Test
    @DisplayName("DOIT retourner true pour une image dans les dimensions acceptées")
    void shouldReturnTrueForValidDimensions() {
        assertThat(visionAnalyzer.isValidImage(imageValide())).isTrue();
    }

    // -------------------------------------------------------------------------
    // Estimation du coût en tokens (estimateTokenCost)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 0 pour une image null")
    void shouldReturnZeroTokenCostForNullImage() {
        assertThat(visionAnalyzer.estimateTokenCost(null)).isZero();
    }

    @Test
    @DisplayName("DOIT retourner au minimum 85 tokens pour une petite image")
    void shouldReturnMinimum85TokensForSmallImage() {
        assertThat(visionAnalyzer.estimateTokenCost(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)))
            .isGreaterThanOrEqualTo(85);
    }

    @Test
    @DisplayName("DOIT calculer le coût proportionnellement aux dimensions (w*h/750, min 85)")
    void shouldCalculateTokenCostProportionallyToDimensions() {
        BufferedImage grande = new BufferedImage(1500, 1500, BufferedImage.TYPE_INT_RGB);
        int cout = visionAnalyzer.estimateTokenCost(grande);
        assertThat(cout).isEqualTo(1500 * 1500 / 750); // 3000
    }

    // -------------------------------------------------------------------------
    // Statistiques (getStats)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT déléguer les statistiques aux VisionProperties")
    void shouldDelegateStatisticsToVisionProperties() {
        when(props.getTimeoutSeconds()).thenReturn(30);
        when(props.getMaxTokens()).thenReturn(2000);
        when(props.getImageQuality()).thenReturn("high");
        when(props.getPrompt()).thenReturn(PROMPT);

        VisionAnalyzer.VisionStats stats = visionAnalyzer.getStats();

        assertThat(stats.timeoutSeconds()).isEqualTo(30);
        assertThat(stats.maxTokens()).isEqualTo(2000);
        assertThat(stats.imageQuality()).isEqualTo("high");
        assertThat(stats.defaultPrompt()).isEqualTo(PROMPT);
    }
}
