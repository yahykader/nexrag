# Research: Phase 3 — Retrieval Pipeline

**Branch**: `003-phase3-retrieval` | **Date**: 2026-03-28

## Summary of Findings

All production classes exist and are implemented. No unknowns remain. This document captures design decisions relevant to test strategy.

---

## Decision 1: Mock Strategy for `QueryTransformerService`

**Decision**: Mock `ChatLanguageModel` via `@Mock` and inject a test-configured `RetrievalConfig` with `method: rule-based` to make tests deterministic.

**Rationale**: `QueryTransformerService` has three transformation paths: `llm`, `rule-based`, and `hybrid`. The LLM path calls `chatModel.generate(prompt)` which would require a live model. Using `rule-based` mode (configured via `RetrievalConfig`) makes all variants deterministic. The LLM path is covered separately by mocking `chatModel.generate()` to return a controlled JSON array.

**Alternatives considered**:
- Spring Boot Test slice with WireMock: rejected — overkill for a unit test, introduces network infrastructure.
- Integration test: deferred to Phase 9.

---

## Decision 2: `RetrievalConfig` — Mock vs. Real Instance

**Decision**: Construct a real `RetrievalConfig` instance with test-specific values using the builder/setter pattern (Lombok `@Data`) rather than mocking it.

**Rationale**: `RetrievalConfig` is a `@ConfigurationProperties` POJO (Lombok `@Data`). Creating a real instance with test values is cleaner and faster than mocking 10+ property accessors. Tests can call `config.getQueryTransformer().setMaxVariants(5)` etc. This also prevents fragile `when(config.getX().getY()).thenReturn(...)` chains.

**Alternatives considered**:
- `@MockBean` via `@SpringBootTest`: rejected — too heavy, violates < 500 ms constraint.
- YAML test properties file: acceptable alternative but builder pattern requires no file I/O.

---

## Decision 3: `ParallelRetrieverService` Timeout Testing

**Decision**: Use `CompletableFuture.supplyAsync(() -> { Thread.sleep(6000); ... })` in the mock `Retriever.retrieveAsync()` to simulate a timeout exceeding the configured 5 000 ms threshold.

**Rationale**: The production code uses `CompletableFuture.allOf(...).get(parallelTimeout, MILLISECONDS)`. For the timeout test (AC-9.2), the slow mock must exceed this threshold. The test must configure `parallelTimeout` to a small value (e.g., 200 ms) and the slow mock to sleep 1 000 ms so the test itself completes in < 500 ms.

**Key configuration**: Set `config.getRetrievers().setParallelTimeout(200L)` in the test, slow mock sleeps 1 000 ms. Partial results from the fast mock must be returned.

**Alternatives considered**:
- Awaitility polling: rejected — adds dependency, not needed here.
- Real Thread.sleep in test: acceptable but must keep timeout < 500 ms total.

---

## Decision 4: `ContentAggregatorService` — Deduplication Clarification

**Decision**: Test deduplication using same-ID chunks, not cosine similarity. Document the gap between spec and implementation.

**Rationale**: The production `deduplicateChunks()` method removes duplicates by **chunk ID** (exact string match), retaining the higher-scored entry. The spec and test plan (AC-10.2) reference "cosine similarity > 0.95" — but this is not implemented in production. The content similarity deduplication is a **future enhancement**. Test AC-10.2 against the actual behavior: same-ID chunks → deduplicate; different-ID near-identical content → NOT deduplicated by the current implementation.

**Gap noted**: `FR-011` in the spec says "content similarity exceeds configured threshold". The current implementation deduplicates by ID only. Tests must match the current implementation, not the spec intent. This gap should be raised as a backlog item.

---

## Decision 5: `CrossEncoderReranker` — `@ConditionalOnProperty`

**Decision**: Instantiate `CrossEncoderReranker` directly (bypassing Spring) by calling the constructor with mock `RetrievalConfig` and mock `RAGMetrics`.

**Rationale**: `CrossEncoderReranker` has `@ConditionalOnProperty(name = "retrieval.reranker.enabled", havingValue = "true")`. In a `@SpringBootTest` context it would not load if the property is false. Using `@ExtendWith(MockitoExtension.class)` and direct instantiation avoids this issue entirely. The reranker is a pure function (no I/O) — its simulated scoring logic is fully testable without Spring context.

**Alternatives considered**:
- `@TestPropertySource(properties = "retrieval.reranker.enabled=true")`: valid but requires Spring context overhead.

---

## Decision 6: `RetrievalAugmentorOrchestrator` Test Scope

**Decision**: `RetrievalAugmentorOrchestratorSpec` covers orchestration logic only — verify that each step is called in sequence and that `RAGMetrics` records are invoked. Individual step correctness is covered by each sub-spec.

**Rationale**: The orchestrator delegates everything to 5 injectable services. Its own logic is: call step 1–5 in order, record metrics, return a `RetrievalAugmentorResult`. Tests verify delegation contracts and the success/failure result shape. Deep behavioral assertions belong in the individual `*Spec.java` classes.

---

## Decision 7: RRF Fusion — Weight Verification

**Decision**: Test RRF fusion with known inputs and verify the mathematical score output, confirming the `text:0.5 / image:0.3 / BM25:0.2` weight application.

**Rationale**: The production code uses standard RRF formula `1 / (k + rank + 1)` applied per retriever. However, reviewing the `applyRRFFusion()` method in `ContentAggregatorService`, the weights (`text-weight: 0.5`, `image-weight: 0.3`, `bm25-weight: 0.2`) defined in `application.yml` are **not currently applied** in the RRF computation — all retrievers contribute with equal weight using the basic RRF formula. The weighted RRF is a configuration value that is present but not yet enforced in the aggregation logic.

**Gap noted**: The spec (FR-009) and clarification specify weighted RRF. The production code does not apply source-specific weights in `applyRRFFusion()`. Tests should verify current behavior (equal-weight RRF) and this gap should be logged as a backlog item for weighted RRF implementation.

---

## Acceptance Criteria Traceability

| AC | Spec Story | Production Class | Test Class | Notes |
|----|-----------|-----------------|------------|-------|
| AC-8.1 | US-8 | QueryTransformerService | QueryTransformerServiceSpec | rule-based: synonym expansion |
| AC-8.2 | US-8 | QueryRouterService | QueryRouterServiceSpec | IMAGE_ONLY strategy for "image"/"photo" queries |
| AC-8.3 | US-8 | QueryRouterService | QueryRouterServiceSpec | HYBRID strategy for general queries |
| AC-9.1 | US-9 | ParallelRetrieverService | ParallelRetrieverServiceSpec | Merged results from all retrievers |
| AC-9.2 | US-9 | ParallelRetrieverService | ParallelRetrieverServiceSpec | Timeout: partial results returned |
| AC-9.3 | US-9 | ParallelRetrieverService | ParallelRetrieverServiceSpec | Results sorted by descending score |
| AC-10.1 | US-10 | CrossEncoderReranker | CrossEncoderRerankerSpec | Best score first after reranking |
| AC-10.2 | US-10 | ContentAggregatorService | ContentAggregatorServiceSpec | ID-based dedup (gap: not cosine similarity) |
| AC-10.3 | US-10 | ContentInjectorService | ContentInjectorServiceSpec | Token budget not exceeded |

## Known Gaps (Backlog Items)

1. **Cosine similarity deduplication** (FR-011 vs. current implementation): Production deduplicates by ID. Content-similarity deduplication is not implemented.
2. **Weighted RRF** (FR-009 vs. current implementation): Source weights (text:0.5/image:0.3/bm25:0.2) are configured but not applied in `applyRRFFusion()`.
3. **Cross-encoder reranker**: Currently a stub (`calculateSimulatedRerankScore`). Real cross-encoder inference is not implemented.
