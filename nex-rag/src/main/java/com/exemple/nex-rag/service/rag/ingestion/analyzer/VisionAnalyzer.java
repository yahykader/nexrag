package com.exemple.nexrag.service.rag.ingestion.analyzer;

import com.exemple.nexrag.service.rag.ingestion.analyzer.ImageConverter;
import com.exemple.nexrag.service.rag.ingestion.analyzer.VisionFallbackGenerator;
import com.exemple.nexrag.service.rag.ingestion.analyzer.VisionProperties;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service d'analyse d'images via Vision AI avec Circuit Breaker.
 *
 * Principe SRP : unique responsabilité → orchestrer l'appel Vision AI.
 *                La conversion Base64 est dans {@link ImageConverter}.
 *                Le fallback est dans {@link VisionFallbackGenerator}.
 *                Les prompts/types sont dans {@link AnalysisType}.
 * Clean code   : élimine 5 méthodes spécialisées quasi-identiques — remplacées
 *                par {@code analyze(image, AnalysisType)} paramétré.
 *                Élimine 5 fallbacks dupliqués — un seul délégant au générateur.
 *
 * @author RAG Team
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisionAnalyzer {

    private final ChatLanguageModel      visionModel;
    private final ImageConverter         imageConverter;
    private final VisionFallbackGenerator fallbackGenerator;
    private final VisionProperties       props;

    // -------------------------------------------------------------------------
    // API publique — méthodes spécialisées
    // -------------------------------------------------------------------------

    /** Analyse générale (prompt par défaut configuré). */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallback")
    public String analyzeImage(BufferedImage image) throws IOException {
        return execute(image, props.getPrompt());
    }

    /** Analyse avec prompt libre. */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackWithPrompt")
    public String analyzeImage(BufferedImage image, String prompt) throws IOException {
        return execute(image, prompt);
    }

    /** Extraction de texte (OCR-like). */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackTyped")
    public String extractText(BufferedImage image) throws IOException {
        return execute(image, AnalysisType.TEXT);
    }

    /** Détection d'objets. */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackTyped")
    public String detectObjects(BufferedImage image) throws IOException {
        return execute(image, AnalysisType.OBJECTS);
    }

    /** Description de scène. */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackTyped")
    public String describeScene(BufferedImage image) throws IOException {
        return execute(image, AnalysisType.SCENE);
    }

    /** Analyse de chart/graphique. */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackTyped")
    public String analyzeChart(BufferedImage image) throws IOException {
        return execute(image, AnalysisType.CHART);
    }

    /** Résumé de page de document. */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackTyped")
    public String summarizeDocument(BufferedImage image) throws IOException {
        return execute(image, AnalysisType.DOCUMENT);
    }

    // -------------------------------------------------------------------------
    // Batch
    // -------------------------------------------------------------------------

    /**
     * Analyse plusieurs images séquentiellement.
     * Chaque appel est protégé individuellement par le Circuit Breaker.
     */
    public List<String> analyzeImages(List<BufferedImage> images) throws IOException {
        if (images == null || images.isEmpty()) return Collections.emptyList();

        log.info("🔍 [VisionAI] Analyse batch : {} images", images.size());
        List<String> results = new ArrayList<>(images.size());

        for (int i = 0; i < images.size(); i++) {
            try {
                results.add(analyzeImage(images.get(i)));
                if ((i + 1) % 10 == 0) {
                    log.info("📊 [VisionAI] Progression : {}/{}", i + 1, images.size());
                }
            } catch (Exception e) {
                log.error("❌ [VisionAI] Erreur image {}/{} : {}", i + 1, images.size(), e.getMessage());
                results.add("[Erreur analyse : " + e.getMessage() + "]");
            }
        }

        log.info("✅ [VisionAI] Batch terminé : {} résultats", results.size());
        return results;
    }

    // -------------------------------------------------------------------------
    // Validation / Statistiques
    // -------------------------------------------------------------------------

    public boolean isValidImage(BufferedImage image) {
        if (image == null) return false;
        int w = image.getWidth(), h = image.getHeight();
        if (w < 10  || h < 10)   { log.warn("⚠️ [VisionAI] Image trop petite : {}x{}", w, h);  return false; }
        if (w > 4096 || h > 4096) { log.warn("⚠️ [VisionAI] Image trop grande : {}x{}", w, h); return false; }
        return true;
    }

    /** Estime le coût en tokens (~1 token / 750 pixels, minimum 85). */
    public int estimateTokenCost(BufferedImage image) {
        if (image == null) return 0;
        return Math.max(85, (image.getWidth() * image.getHeight()) / 750);
    }

    public VisionStats getStats() {
        return new VisionStats(
            props.getTimeoutSeconds(),
            props.getMaxTokens(),
            props.getImageQuality(),
            props.getPrompt()
        );
    }

    public record VisionStats(int timeoutSeconds, int maxTokens, String imageQuality, String defaultPrompt) {}

    // -------------------------------------------------------------------------
    // Moteur d'exécution — point unique
    // -------------------------------------------------------------------------

    /**
     * Unique point d'appel Vision AI — élimine la duplication entre
     * toutes les méthodes spécialisées.
     */
    private String execute(BufferedImage image, AnalysisType type) throws IOException {
        return execute(image, type.getPrompt());
    }

    private String execute(BufferedImage image, String prompt) throws IOException {
        if (image == null) throw new IllegalArgumentException("Image ne peut pas être null");

        long start = System.currentTimeMillis();

        log.debug("🔍 [VisionAI] Analyse : {}x{} px", image.getWidth(), image.getHeight());

        String base64  = imageConverter.toBase64(image);
        UserMessage msg = UserMessage.from(
            TextContent.from(prompt),
            ImageContent.from(base64, imageConverter.mimeType())
        );

        var response = visionModel.generate(msg);

        if (response == null || response.content() == null) {
            throw new IOException("Vision AI n'a pas retourné de réponse");
        }

        String description = response.content().text();
        if (description == null || description.isBlank()) {
            throw new IOException("Vision AI a retourné une description vide");
        }

        log.info("✅ [VisionAI] {} caractères en {}ms",
            description.length(), System.currentTimeMillis() - start);

        return description;
    }

    // -------------------------------------------------------------------------
    // Fallbacks Circuit Breaker — un seul mécanisme
    // -------------------------------------------------------------------------

    private String fallback(BufferedImage image, Exception e) {
        return fallbackGenerator.generate(image, AnalysisType.DEFAULT, e);
    }

    private String fallbackWithPrompt(BufferedImage image, String prompt, Exception e) {
        return fallbackGenerator.generate(image, AnalysisType.detect(prompt), e);
    }

    private String fallbackTyped(BufferedImage image, Exception e) {
        return fallbackGenerator.generate(image, AnalysisType.DEFAULT, e);
    }
}