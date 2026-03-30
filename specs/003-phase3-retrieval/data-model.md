# Data Model: Phase 3 — Retrieval Pipeline

**Branch**: `003-phase3-retrieval` | **Date**: 2026-03-28

All model classes are Java records/Lombok `@Data @Builder` POJOs in `com.exemple.nexrag.service.rag.retrieval.model`.

---

## Entity: `QueryTransformResult`

**Package**: `model.QueryTransformResult`
**Class type**: Lombok `@Data @Builder`

| Field | Type | Constraint |
|-------|------|-----------|
| `originalQuery` | `String` | Non-null, non-blank |
| `variants` | `List<String>` | Size = max-variants (default 5); always contains originalQuery |
| `method` | `String` | One of: `"llm"`, `"rule-based"`, `"hybrid"`, `"rule-based-fallback"`, `"disabled"` |
| `durationMs` | `long` | ≥ 0 |
| `confidence` | `double` | Range [0.0, 1.0] |

**Validation rules**:
- `variants` MUST contain at least 1 element (the original query)
- `variants` MUST be capped at `config.queryTransformer.maxVariants` (default: 5)
- When `method = "disabled"`, `variants` contains only the original query

---

## Entity: `RoutingDecision`

**Package**: `model.RoutingDecision`
**Class type**: Lombok `@Data @Builder`

| Field | Type | Constraint |
|-------|------|-----------|
| `strategy` | `Strategy` enum | One of: `TEXT_ONLY`, `IMAGE_ONLY`, `HYBRID`, `STRUCTURED` |
| `confidence` | `double` | Range [0.0, 1.0]; ≥ 0.6 when patterns matched |
| `retrievers` | `Map<String, RetrieverConfig>` | Keys: `"text"`, `"image"`, `"bm25"` |
| `estimatedTotalDurationMs` | `long` | Max latency across enabled retrievers |
| `parallelExecution` | `boolean` | Always `true` in current implementation |

**Nested: `RetrieverConfig`**

| Field | Type | Constraint |
|-------|------|-----------|
| `enabled` | `boolean` | Determines if retriever is called |
| `priority` | `Priority` enum | `LOW`, `MEDIUM`, `HIGH` |
| `topK` | `int` | ≥ 0; 0 when disabled |
| `estimatedLatencyMs` | `long` | ≥ 0 |

**Strategy selection rules**:
- `IMAGE_ONLY`: image keywords present, no text/structured keywords
- `STRUCTURED`: structured keywords present, no image keywords
- `TEXT_ONLY`: text/explanation keywords present, no image/structured keywords
- `HYBRID` (default): all other cases

---

## Entity: `RetrievalResult`

**Package**: `model.RetrievalResult`
**Class type**: Lombok `@Data @Builder`

| Field | Type | Constraint |
|-------|------|-----------|
| `retrieverName` | `String` | Non-null: `"text"`, `"image"`, or `"bm25"` |
| `chunks` | `List<ScoredChunk>` | Sorted descending by `score`; max size = topK |
| `totalFound` | `int` | Total matches before topK truncation |
| `topScore` | `double` | Score of first chunk; 0.0 if empty |
| `durationMs` | `long` | Retriever execution time |
| `cacheHits` | `int` | ≥ 0 |
| `cacheMisses` | `int` | ≥ 0 |

**Nested: `ScoredChunk`**

| Field | Type | Constraint |
|-------|------|-----------|
| `id` | `String` | Unique within the store; used for deduplication |
| `content` | `String` | Non-null passage text |
| `metadata` | `Map<String, Object>` | Source file, page, type |
| `score` | `double` | Similarity score; ≥ configured threshold |
| `retrieverName` | `String` | Which retriever produced this chunk |
| `rank` | `int` | 0-based position in retriever's result list |

---

## Entity: `AggregatedContext`

**Package**: `model.AggregatedContext`
**Class type**: Lombok `@Data @Builder`

| Field | Type | Constraint |
|-------|------|-----------|
| `chunks` | `List<SelectedChunk>` | Final ranked list; size ≤ `reranker.topK` (default: 10) |
| `inputChunks` | `int` | Total chunks before deduplication |
| `deduplicatedChunks` | `int` | Unique chunks after ID-based dedup |
| `rrfCandidates` | `int` | Chunks after RRF fusion, before final top-K cut |
| `finalSelected` | `int` | `chunks.size()` |
| `fusionMethod` | `String` | `"rrf"` or `"rrf+reranking"` |
| `durationMs` | `long` | Aggregation execution time |
| `chunksByRetriever` | `Map<String, Integer>` | Count of final chunks per retriever |

**Nested: `SelectedChunk`**

| Field | Type | Constraint |
|-------|------|-----------|
| `id` | `String` | Same as `ScoredChunk.id`; globally unique in result |
| `content` | `String` | Passage text |
| `metadata` | `Map<String, Object>` | Preserved from source |
| `finalScore` | `double` | RRF score (or reranking score if reranker enabled) |
| `scoresByRetriever` | `Map<String, Double>` | Individual RRF contribution per retriever |
| `retrieversUsed` | `List<String>` | Which retrievers retrieved this chunk |

**Deduplication note**: Deduplication is by `ScoredChunk.id` (exact match). When two retrievers return the same chunk ID, the highest-scoring copy is retained before RRF.

---

## Entity: `InjectedPrompt`

**Package**: `model.InjectedPrompt`
**Class type**: Lombok `@Data @Builder`

| Field | Type | Constraint |
|-------|------|-----------|
| `fullPrompt` | `String` | Complete assembled prompt; token count ≤ maxTokens |
| `structure` | `PromptStructure` | Token breakdown by section |
| `sources` | `List<SourceReference>` | One entry per chunk used |
| `contextUsagePercent` | `double` | (totalTokens / maxTokens) × 100 |
| `durationMs` | `long` | Injection execution time |

**Nested: `PromptStructure`**

| Field | Type | Notes |
|-------|------|-------|
| `systemPrompt` | `String` | Fixed preamble (~200 tokens) |
| `documentsContext` | `String` | Injected passages with citations |
| `userQuery` | `String` | Original user query |
| `instructions` | `String` | Fixed response format instructions (~250 tokens) |
| `totalTokens` | `int` | Sum of all section tokens; MUST be ≤ maxTokens (200 000) |

**Token estimation**: 1 token ≈ 4 characters (`TOKENS_PER_CHAR = 0.25`).

---

## Entity: `RetrievalAugmentorOrchestrator.RetrievalAugmentorResult`

**Inner class** of `RetrievalAugmentorOrchestrator`

| Field | Type | Notes |
|-------|------|-------|
| `originalQuery` | `String` | Input query |
| `success` | `boolean` | `false` if any step throws |
| `errorMessage` | `String` | Non-null only when `success = false` |
| `transformResult` | `QueryTransformResult` | Step 1 output |
| `routingDecision` | `RoutingDecision` | Step 2 output |
| `retrievalResults` | `Map<String, RetrievalResult>` | Step 3 output; key = retriever name |
| `aggregatedContext` | `AggregatedContext` | Step 4 output |
| `injectedPrompt` | `InjectedPrompt` | Step 5 output |
| `totalDurationMs` | `long` | End-to-end duration |

---

## Configuration Model: `RetrievalConfig` (relevant fields)

All values from `application.yml` under `retrieval:`:

| Config path | Default | Used by |
|-------------|---------|---------|
| `query-transformer.enabled` | `true` | QueryTransformerService |
| `query-transformer.method` | `llm` | QueryTransformerService |
| `query-transformer.max-variants` | `5` | QueryTransformerService |
| `query-router.enabled` | `true` | QueryRouterService |
| `query-router.default-strategy` | `HYBRID` | QueryRouterService |
| `query-router.confidence-threshold` | `0.7` | QueryRouterService |
| `retrievers.parallel-timeout` | `5000` (ms) | ParallelRetrieverService |
| `retrievers.text.top-k` | `20` | QueryRouterService, TextVectorRetriever |
| `retrievers.image.top-k` | `5` | QueryRouterService, ImageVectorRetriever |
| `retrievers.bm25.top-k` | `10` | QueryRouterService, BM25Retriever |
| `retrievers.text.similarity-threshold` | `0.7` | TextVectorRetriever |
| `retrievers.image.similarity-threshold` | `0.6` | ImageVectorRetriever |
| `aggregator.rrf-k` | `60` | ContentAggregatorService |
| `aggregator.max-candidates` | `30` | ContentAggregatorService |
| `aggregator.final-top-k` | `10` | ContentAggregatorService |
| `reranker.enabled` | `true` | ContentAggregatorService, CrossEncoderReranker |
| `reranker.top-k` | `10` | ContentAggregatorService |
| `content-injector.max-tokens` | `200000` | ContentInjectorService |

---

## State Transitions: Pipeline Flow

```
User Query (String)
    ↓ QueryTransformerService.transform()
QueryTransformResult (5 variants)
    ↓ QueryRouterService.route()
RoutingDecision (strategy + retriever configs)
    ↓ ParallelRetrieverService.retrieveParallel()
Map<String, RetrievalResult>  (one per active retriever)
    ↓ ContentAggregatorService.aggregate()
AggregatedContext (RRF-fused, deduplicated, top-10)
    ↓ ContentInjectorService.injectContext()
InjectedPrompt (≤ 200k tokens, with citations)
    ↓ RetrievalAugmentorOrchestrator returns
RetrievalAugmentorResult
```
