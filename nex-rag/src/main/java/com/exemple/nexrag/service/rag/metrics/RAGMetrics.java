package com.exemple.nexrag.service.rag.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Métriques centralisées du système RAG.
 *
 * Principe SRP  : unique responsabilité → enregistrer et exposer
 *                 les métriques Prometheus de toutes les couches RAG.
 * Clean code    : supprime {@code System.out.println} du constructeur.
 *                 {@code incrementCustomCounter} utilise le cache —
 *                 évite la re-création d'un counter à chaque appel.
 *                 {@code getSummary()} extrait dans un log dédié.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Component
public class RAGMetrics {

    private final MeterRegistry registry;

    // -------------------------------------------------------------------------
    // Gauges — valeurs courantes
    // -------------------------------------------------------------------------
    private final AtomicInteger activeIngestions       = new AtomicInteger(0);
    private final AtomicInteger activeQueries          = new AtomicInteger(0);
    private final AtomicLong    totalFilesProcessed    = new AtomicLong(0);
    private final AtomicLong    totalQueriesProcessed  = new AtomicLong(0);
    private final AtomicLong    totalTokensGenerated   = new AtomicLong(0);

    // -------------------------------------------------------------------------
    // Caches des meters — évite le re-enregistrement à chaud
    // -------------------------------------------------------------------------
    private final Map<String, Counter>                  counterCache     = new ConcurrentHashMap<>();
    private final Map<String, Timer>                    timerCache       = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>>  customGaugeCache = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    public RAGMetrics(MeterRegistry registry) {
        this.registry = registry;
        registerGauges();
        log.info("✅ RAGMetrics initialisé — monitoring complet actif");
    }

    private void registerGauges() {
        Gauge.builder("rag_active_ingestions", activeIngestions, AtomicInteger::get)
            .description("Files currently being ingested")
            .tag("component", "ingestion")
            .register(registry);

        Gauge.builder("rag_active_queries", activeQueries, AtomicInteger::get)
            .description("Queries currently being processed")
            .tag("component", "retrieval")
            .register(registry);

        Gauge.builder("rag_total_files", totalFilesProcessed, AtomicLong::get)
            .description("Total files processed since startup")
            .tag("component", "ingestion")
            .register(registry);

        Gauge.builder("rag_total_queries", totalQueriesProcessed, AtomicLong::get)
            .description("Total queries processed since startup")
            .tag("component", "retrieval")
            .register(registry);

        Gauge.builder("rag_total_tokens", totalTokensGenerated, AtomicLong::get)
            .description("Total tokens generated since startup")
            .tag("component", "generation")
            .register(registry);
    }

    // -------------------------------------------------------------------------
    // Helpers — cache des meters
    // -------------------------------------------------------------------------

    private static String key(String name, String... tags) {
        return name + "|" + String.join("|", tags);
    }

    private Counter counter(String name, String description, String... tags) {
        return counterCache.computeIfAbsent(key(name, tags), k ->
            Counter.builder(name)
                .description(description)
                .tags(tags)
                .register(registry)
        );
    }

    private Timer timer(String name, String description, String... tags) {
        return timerCache.computeIfAbsent(key(name, tags), k ->
            Timer.builder(name)
                .description(description)
                .tags(tags)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry)
        );
    }

    private static double safeDouble(Double v) {
        if (v == null || v.isNaN() || v.isInfinite()) return 0.0;
        return v;
    }

    // -------------------------------------------------------------------------
    // Ingestion
    // -------------------------------------------------------------------------

    public void recordIngestionSuccess(String strategy, long durationMs, int embeddings) {
        counter("rag_ingestion_files_total", "Total files ingested",
            "strategy", strategy, "status", "success").increment();

        timer("rag_ingestion_duration_seconds", "File ingestion duration",
            "strategy", strategy).record(Duration.ofMillis(durationMs));

        if (embeddings > 0) {
            counter("rag_embeddings_total", "Total embeddings created",
                "strategy", strategy).increment(embeddings);
        }

        totalFilesProcessed.incrementAndGet();
    }

    public void recordIngestionError(String strategy, String errorType) {
        counter("rag_ingestion_files_total", "Total files ingested",
            "strategy", strategy, "status", "error").increment();

        counter("rag_ingestion_errors_total", "Total ingestion errors",
            "strategy", strategy, "error_type", errorType).increment();
    }

    public void startIngestion()  { activeIngestions.incrementAndGet(); }
    public void endIngestion()    { activeIngestions.decrementAndGet();  }

    public void recordDuplicate(String strategy) {
        counter("rag_duplicates_total", "Total duplicate files detected",
            "strategy", strategy).increment();
    }

    public void recordVirusDetected(String virusName) {
        counter("rag_viruses_detected_total", "Total viruses detected",
            "virus", virusName != null ? virusName : "unknown").increment();
    }

    public void recordStrategyProcessing(String strategy, long durationMs, int chunks) {
        timer("rag_strategy_processing_duration_seconds", "Strategy processing duration",
            "strategy", strategy).record(Duration.ofMillis(durationMs));

        counter("rag_strategy_chunks_total", "Total chunks extracted by strategy",
            "strategy", strategy).increment(chunks);
    }

    public void recordVectorStoreOperation(String operation, long durationMs, int count) {
        timer("rag_vectorstore_operation_duration_seconds", "Vector store operation duration",
            "operation", operation).record(Duration.ofMillis(durationMs));

        counter("rag_vectorstore_operations_total", "Total vector store operations",
            "operation", operation).increment(count);
    }

    public void recordAntivirusScan(long durationMs, boolean clean) {
        String result = clean ? "clean" : "infected";
        timer("rag_antivirus_scan_duration_seconds", "Antivirus scan duration",
            "result", result).record(Duration.ofMillis(durationMs));

        counter("rag_antivirus_scans_total", "Total antivirus scans",
            "result", result).increment();
    }

    // -------------------------------------------------------------------------
    // Retrieval
    // -------------------------------------------------------------------------

    public void recordQueryTransformation(long durationMs, int variants) {
        timer("rag_query_transformation_duration_seconds", "Query transformation duration")
            .record(Duration.ofMillis(durationMs));

        counter("rag_query_variants_total", "Total query variants generated")
            .increment(variants);
    }

    public void recordRoutingDecision(String strategy, double confidence) {
        counter("rag_routing_decisions_total", "Total routing decisions",
            "strategy", strategy).increment();
    }

    public void recordRetrieval(String retriever, long durationMs, int chunks) {
        timer("rag_retrieval_duration_seconds", "Retrieval duration",
            "retriever", retriever).record(Duration.ofMillis(durationMs));

        counter("rag_chunks_retrieved_total", "Total chunks retrieved",
            "retriever", retriever).increment(chunks);
    }

    public void recordAggregation(long durationMs, int inputChunks, int outputChunks) {
        timer("rag_aggregation_duration_seconds", "Content aggregation duration")
            .record(Duration.ofMillis(durationMs));
    }

    public void recordReranking(long durationMs, int chunksReranked) {
        timer("rag_reranking_duration", "Reranking duration")
            .record(durationMs, TimeUnit.MILLISECONDS);

        counter("rag_reranking_chunks_total", "Total chunks reranked")
            .increment(chunksReranked);
    }

    public void recordRerankingWithStrategy(long durationMs, int chunksReranked, String strategy) {
        timer("rag_reranking_duration", "Reranking duration",
            "strategy", strategy).record(durationMs, TimeUnit.MILLISECONDS);

        counter("rag_reranking_chunks_total", "Total chunks reranked",
            "strategy", strategy).increment(chunksReranked);
    }

    public void startQuery() { activeQueries.incrementAndGet(); }
    public void endQuery()   {
        activeQueries.decrementAndGet();
        totalQueriesProcessed.incrementAndGet();
    }

    // -------------------------------------------------------------------------
    // Generation
    // -------------------------------------------------------------------------

    public void recordGeneration(long durationMs, int tokens) {
        timer("rag_generation_duration_seconds", "Text generation duration")
            .record(Duration.ofMillis(durationMs));

        counter("rag_tokens_generated_total", "Total tokens generated")
            .increment(tokens);

        totalTokensGenerated.addAndGet(tokens);
    }

    public void recordCitation() {
        counter("rag_citations_total", "Total citations detected").increment();
    }

    public void recordConversationMessage(String role, int tokens) {
        counter("rag_conversation_messages_total", "Total conversation messages",
            "role", role).increment();

        counter("rag_conversation_tokens_total", "Total conversation tokens",
            "role", role).increment(tokens);
    }

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------

    public void recordApiCall(String operation, long durationMs) {
        counter("rag_api_calls_total", "Total API calls",
            "service", "openai", "operation", operation).increment();

        timer("rag_api_duration_seconds", "API call duration",
            "service", "openai", "operation", operation).record(Duration.ofMillis(durationMs));
    }

    public void recordApiError(String operation) {
        counter("rag_api_errors_total", "Total API errors",
            "service", "openai", "operation", operation).increment();
    }

    // -------------------------------------------------------------------------
    // Cache
    // -------------------------------------------------------------------------

    public void recordCacheHit(String cacheType) {
        counter("rag_cache_hits_total", "Total cache hits", "cache", cacheType).increment();
    }

    public void recordCacheMiss(String cacheType) {
        counter("rag_cache_misses_total", "Total cache misses", "cache", cacheType).increment();
    }

    // -------------------------------------------------------------------------
    // Pipeline
    // -------------------------------------------------------------------------

    public void recordPipeline(long totalDuration, long retrievalDuration, long generationDuration) {
        timer("rag_pipeline_duration_seconds", "Complete RAG pipeline duration")
            .record(Duration.ofMillis(totalDuration));
    }

    // -------------------------------------------------------------------------
    // Coûts LLM
    // -------------------------------------------------------------------------

    public void recordLLMCost(double costUSD, int inputTokens, int outputTokens) {
        counter("rag_cost_llm_usd",            "Total LLM cost in USD").increment(costUSD);
        counter("rag_cost_input_tokens_total",  "Total input tokens").increment(inputTokens);
        counter("rag_cost_output_tokens_total", "Total output tokens").increment(outputTokens);
    }

    public void recordLLMCostByModel(String model, double costUSD, int inputTokens, int outputTokens) {
        counter("rag_cost_llm_total_usd",      "Total LLM cost in USD",  "model", model).increment(costUSD);
        counter("rag_cost_input_tokens_total",  "Total input tokens",     "model", model).increment(inputTokens);
        counter("rag_cost_output_tokens_total", "Total output tokens",    "model", model).increment(outputTokens);
    }

    public void recordOperationCost(String operation, double costUSD) {
        counter("rag_cost_operation_usd", "Cost by operation type",
            "operation", operation).increment(costUSD);
    }

    // -------------------------------------------------------------------------
    // Métriques custom
    // -------------------------------------------------------------------------

    /**
     * Gauge custom — mis à jour sans re-créer le meter.
     */
    public void recordCustomMetric(String name, String tag, double value) {
        AtomicReference<Double> ref = customGaugeCache.computeIfAbsent(name + "|service=" + tag, k -> {
            AtomicReference<Double> ar = new AtomicReference<>(0.0);
            Gauge.builder(name, ar, r -> safeDouble(r.get()))
                .tags(Tags.of("service", tag))
                .register(registry);
            return ar;
        });
        ref.set(value);
    }

    /**
     * Counter custom — utilise le cache comme tous les autres counters.
     * ✅ Fix : ne re-crée plus un counter à chaque appel.
     */
    public void incrementCustomCounter(String name, String... tags) {
        counter(name, "Custom counter — " + name, tags).increment();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public int  getActiveIngestions()      { return activeIngestions.get();      }
    public int  getActiveQueries()         { return activeQueries.get();         }
    public long getTotalFilesProcessed()   { return totalFilesProcessed.get();   }
    public long getTotalQueriesProcessed() { return totalQueriesProcessed.get(); }
    public long getTotalTokensGenerated()  { return totalTokensGenerated.get();  }

    public void logSummary() {
        log.info("📊 RAGMetrics — files={} queries={} tokens={} activeIngestions={} activeQueries={}",
            totalFilesProcessed.get(), totalQueriesProcessed.get(), totalTokensGenerated.get(),
            activeIngestions.get(), activeQueries.get());
    }
}