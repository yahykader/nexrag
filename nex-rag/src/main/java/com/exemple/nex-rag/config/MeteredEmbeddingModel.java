package com.exemple.nexrag.service.rag.embedding;

import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Décorateur ajoutant des métriques à tout {@link EmbeddingModel}.
 *
 * Principe SRP  : unique responsabilité → instrumenter les appels d'embedding.
 * Principe OCP  : ouvert à l'extension (nouveaux modèles), fermé à la modification.
 * Principe LSP  : substituable à tout EmbeddingModel sans changer le comportement.
 * Principe DIP  : dépend de l'abstraction EmbeddingModel, pas d'une implémentation.
 *
 * Pattern utilisé : Décorateur (Decorator).
 *
 * @author ayahyaoui
 * @version 1.0
 */
@RequiredArgsConstructor
public class MeteredEmbeddingModel implements EmbeddingModel {

    private static final String OP_EMBED      = "embed_text";
    private static final String OP_EMBED_BATCH = "embed_text_batch";

    private final EmbeddingModel delegate;
    private final RAGMetrics     ragMetrics;

    // -------------------------------------------------------------------------
    // EmbeddingModel API
    // -------------------------------------------------------------------------

    @Override
    public Response<Embedding> embed(String text) {
        return executeWithMetrics(OP_EMBED, () -> delegate.embed(text));
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return executeWithMetrics(OP_EMBED, () -> delegate.embed(textSegment));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        return executeWithMetrics(OP_EMBED_BATCH, () -> delegate.embedAll(textSegments));
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }

    // -------------------------------------------------------------------------
    // Infrastructure interne
    // -------------------------------------------------------------------------

    /**
     * Exécute une opération en enregistrant la durée et les erreurs.
     *
     * @param operation nom de l'opération pour les métriques
     * @param callable  logique à exécuter
     * @param <T>       type de retour
     * @return résultat de l'opération
     */
    private <T> T executeWithMetrics(String operation, MetricCallable<T> callable) {
        long start = System.currentTimeMillis();
        try {
            T result = callable.call();
            ragMetrics.recordApiCall(operation, System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException e) {
            ragMetrics.recordApiError(operation);
            throw e;
        }
    }

    @FunctionalInterface
    private interface MetricCallable<T> {
        T call();
    }
}