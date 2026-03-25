package com.exemple.nexrag.service.rag.ingestion.analyzer;

/**
 * Type d'analyse Vision AI.
 *
 * Clean code  : élimine les magic strings {@code "text"}, {@code "objects"},
 *               {@code "chart"}, {@code "scene"}, {@code "default"}
 *               éparpillés dans {@link VisionAnalyzer}.
 * Principe OCP : ajouter un type = ajouter une valeur ici + un prompt dans
 *               {@link VisionPrompts}. {@link VisionAnalyzer} ne change pas.
 */
public enum AnalysisType {

    TEXT(
        "Extract all text visible in this image. " +
        "Return only the text content, preserving line breaks and formatting where possible."
    ),
    OBJECTS(
        "List all objects, items, and elements visible in this image. Be specific and detailed."
    ),
    CHART(
        "This image contains a chart or graph. Describe the type of chart (bar, line, pie, etc.), " +
        "the data it represents, key values, trends, and any labels or legends present."
    ),
    SCENE(
        "Describe the overall scene, setting, and context of this image. " +
        "Include details about the location, atmosphere, and any notable features."
    ),
    DOCUMENT(
        "This image shows a document page. " +
        "Summarize the main content, including any headings, key points, and overall topic."
    ),
    DEFAULT(null); // prompt fourni par VisionProperties

    private final String prompt;

    AnalysisType(String prompt) {
        this.prompt = prompt;
    }

    public boolean hasCustomPrompt() {
        return prompt != null;
    }

    public String getPrompt() {
        return prompt;
    }

    /**
     * Détecte le type d'analyse à partir du contenu d'un prompt libre.
     */
    public static AnalysisType detect(String prompt) {
        if (prompt == null || prompt.isBlank()) return DEFAULT;

        String lower = prompt.toLowerCase();
        if (lower.contains("extract") && lower.contains("text")) return TEXT;
        if (lower.contains("objects") || lower.contains("items"))  return OBJECTS;
        if (lower.contains("chart")   || lower.contains("graph"))  return CHART;
        if (lower.contains("scene")   || lower.contains("setting"))return SCENE;
        return DEFAULT;
    }
}