package com.exemple.nexrag.service.rag.ingestion.strategy;

import java.util.HashMap;
import java.util.Map;

/**
 * Résultat d'une ingestion de document.
 *
 * Principe SRP  : unique responsabilité → porter les statistiques d'une ingestion.
 * Clean code    : immutabilité garantie via {@code Map.copyOf()} —
 *                 toute tentative de modification lève {@link UnsupportedOperationException}.
 *                 Accesseurs en style record ({@code totalEmbeddings()}, pas {@code get...()}).
 *
 * @param textEmbeddings  nombre d'embeddings texte créés (≥ 0)
 * @param imageEmbeddings nombre d'embeddings image créés (≥ 0)
 * @param metadata        métadonnées additionnelles (strategy, filename, status…)
 */
public record IngestionResult(
    int                 textEmbeddings,
    int                 imageEmbeddings,
    Map<String, Object> metadata
) {
    // -------------------------------------------------------------------------
    // Compact constructor — validation + immutabilité
    // -------------------------------------------------------------------------

    public IngestionResult {
        if (textEmbeddings  < 0) throw new IllegalArgumentException("textEmbeddings négatif");
        if (imageEmbeddings < 0) throw new IllegalArgumentException("imageEmbeddings négatif");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Constructeur sans métadonnées. */
    public IngestionResult(int textEmbeddings, int imageEmbeddings) {
        this(textEmbeddings, imageEmbeddings, Map.of());
    }

    // -------------------------------------------------------------------------
    // Prédicats et calculs — style record (pas de préfixe get)
    // -------------------------------------------------------------------------

    public int     totalEmbeddings()    { return textEmbeddings + imageEmbeddings; }
    public boolean hasEmbeddings()      { return totalEmbeddings() > 0;            }
    public boolean hasTextEmbeddings()  { return textEmbeddings   > 0;             }
    public boolean hasImageEmbeddings() { return imageEmbeddings  > 0;             }

    // -------------------------------------------------------------------------
    // Builder immutable
    // -------------------------------------------------------------------------

    /**
     * Retourne une nouvelle instance enrichie d'une entrée de métadonnée.
     */
    public IngestionResult withMetadata(String key, Object value) {
        Map<String, Object> copy = new HashMap<>(metadata);
        copy.put(key, value);
        return new IngestionResult(textEmbeddings, imageEmbeddings, copy);
    }

    // -------------------------------------------------------------------------
    // Fabriques statiques
    // -------------------------------------------------------------------------

    /**
     * Résultat d'erreur (zéro embeddings).
     */
    public static IngestionResult error(String filename, String errorMessage) {
        return new IngestionResult(0, 0, Map.of(
            "filename", filename,
            "error",    errorMessage,
            "status",   "ERROR"
        ));
    }

    /**
     * Résultat de succès avec statistiques.
     */
    public static IngestionResult success(
            int    textEmbeddings,
            int    imageEmbeddings,
            String strategy,
            String filename) {

        return new IngestionResult(textEmbeddings, imageEmbeddings, Map.of(
            "strategy", strategy,
            "filename", filename,
            "status",   "SUCCESS"
        ));
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format(
            "IngestionResult[text=%d, images=%d, total=%d, metadata=%s]",
            textEmbeddings, imageEmbeddings, totalEmbeddings(), metadata
        );
    }
}