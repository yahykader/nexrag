package com.exemple.nexrag.service.rag.metrics.embedding;

import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service d'embedding OpenAI.
 *
 * Principe SRP  : unique responsabilité → générer des embeddings
 *                 via le modèle OpenAI configuré.
 * Clean code    : {@code embedWithMetrics()} factorise le bloc
 *                 try-catch-métrique dupliqué dans les 3 méthodes.
 *                 {@code embedImage()} supprimé — retournait {@code null}
 *                 (contrat cassé). À réintroduire quand un modèle image
 *                 sera disponible.
 *                 {@code Collectors.toList()} → {@code .toList()} (Java 16+).
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiEmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final RAGMetrics     ragMetrics;

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    /**
     * Génère l'embedding d'un texte brut.
     *
     * @param text texte à vectoriser
     * @return embedding résultant
     * @throws RuntimeException si l'appel API échoue
     */
    public Embedding embedText(String text) {
        return embedWithMetrics(
            "embed_text",
            () -> embeddingModel.embed(text).content(),
            "text (%d chars)".formatted(text.length())
        );
    }

    /**
     * Génère l'embedding d'un segment LangChain4j.
     *
     * @param segment segment à vectoriser
     * @return embedding résultant
     * @throws RuntimeException si l'appel API échoue
     */
    public Embedding embedSegment(TextSegment segment) {
        return embedWithMetrics(
            "embed_segment",
            () -> embeddingModel.embed(segment).content(),
            "segment"
        );
    }

    /**
     * Génère les embeddings d'une liste de textes en batch.
     *
     * @param texts liste de textes à vectoriser
     * @return liste d'embeddings dans le même ordre
     * @throws RuntimeException si l'appel API échoue
     */
    public List<Embedding> embedBatch(List<String> texts) {
        List<TextSegment> segments = texts.stream()
            .map(TextSegment::from)
            .toList();                          // ✅ Java 16+ — plus de Collectors.toList()

        return embedWithMetrics(
            "embed_batch",
            () -> embeddingModel.embedAll(segments).content(),
            "batch (%d texts)".formatted(texts.size())
        );
    }

    /** Retourne la dimension des vecteurs produits par le modèle. */
    public int getDimension() {
        return embeddingModel.dimension();
    }

    // -------------------------------------------------------------------------
    // Privé — pipeline embedding + métriques factorisé
    // -------------------------------------------------------------------------

    /**
     * Exécute une opération d'embedding en enregistrant durée et erreurs.
     *
     * <p>Factorise le bloc try-catch-métrique identique dans
     * {@code embedText}, {@code embedSegment} et {@code embedBatch}.
     *
     * @param operation   nom de l'opération pour les métriques
     * @param supplier    lambda qui appelle l'API
     * @param description description pour le log
     * @return résultat du supplier
     * @throws RuntimeException si le supplier lève une exception
     */
    private <T> T embedWithMetrics(String operation,
                                    EmbeddingSupplier<T> supplier,
                                    String description) {
        long start = System.currentTimeMillis();
        try {
            T result   = supplier.get();
            long duration = System.currentTimeMillis() - start;

            ragMetrics.recordApiCall(operation, duration);
            log.debug("✅ Embedding réussi ({}) en {}ms", description, duration);

            return result;

        } catch (Exception e) {
            ragMetrics.recordApiError(operation);
            log.error("❌ Embedding échoué ({}) : {}", description, e.getMessage(), e);
            throw new RuntimeException("Embedding échoué — " + description, e);
        }
    }

    /**
     * Interface fonctionnelle pour les lambdas d'embedding
     * qui déclarent {@code throws Exception}.
     */
    @FunctionalInterface
    private interface EmbeddingSupplier<T> {
        T get() throws Exception;
    }
}