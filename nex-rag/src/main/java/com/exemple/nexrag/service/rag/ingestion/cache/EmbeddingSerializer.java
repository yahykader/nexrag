package com.exemple.nexrag.service.rag.ingestion.cache;

import dev.langchain4j.data.embedding.Embedding;
import org.springframework.stereotype.Component;

/**
 * Sérialisation et désérialisation d'embeddings vers/depuis String Redis.
 *
 * Principe SRP : unique responsabilité → convertir un {@link Embedding}
 *                en représentation textuelle stockable dans Redis.
 * Clean code   : extrait {@code serializeEmbedding()} et
 *                {@code deserializeEmbedding()} hors de {@link EmbeddingCache}.
 *
 * Format : vecteur CSV de floats, ex. {@code "-0.030,0.154,0.872,..."}
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Component
public class EmbeddingSerializer {

    private static final String SEPARATOR = ",";

    /**
     * Sérialise un embedding en chaîne CSV.
     *
     * @param embedding embedding à sérialiser
     * @return représentation CSV des floats
     */
    public String serialize(Embedding embedding) {
        float[]       vector = embedding.vector();
        StringBuilder sb     = new StringBuilder(vector.length * 12);

        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(SEPARATOR);
        }

        return sb.toString();
    }

    /**
     * Désérialise un embedding depuis sa représentation CSV.
     *
     * @param serialized chaîne CSV des floats
     * @return embedding reconstruit
     */
    public Embedding deserialize(String serialized) {
        String[] parts  = serialized.split(SEPARATOR);
        float[]  vector = new float[parts.length];

        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }

        return Embedding.from(vector);
    }
}