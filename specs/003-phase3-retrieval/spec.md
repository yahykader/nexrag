# Feature Specification: Phase 3 — Retrieval Pipeline

**Feature Branch**: `003-phase3-retrieval`
**Created**: 2026-03-28
**Status**: Draft
**Input**: User description: "PHASE 3 — Retrieval pipeline: query transformation, multi-source retrieval, reranking and context injection"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Query Transformation and Intelligent Routing (Priority: P1)

As a RAG system, I want to automatically transform and route user queries so that retrieval is directed to the most relevant sources with maximum recall.

When a user submits a query, the system should expand the query with synonyms and related terms, detect whether the query concerns text or images, and decide which retrieval sources to activate.

**Why this priority**: Query transformation and routing are the entry point of the retrieval pipeline. Without this step all downstream retrieval operates on raw unexpanded queries, significantly reducing recall. It also gates which retrievers are activated, impacting both latency and relevance.

**Independent Test**: Fully testable by submitting different query types and verifying that short/vague queries are expanded and that image-related queries are routed to the image source while generic queries target all sources.

**Acceptance Scenarios**:

1. **Given** a short ambiguous query (e.g., "weather"), **When** the query transformer processes it, **Then** the output contains the original query plus synonyms and related terms extending its coverage
2. **Given** a query containing visual intent keywords (e.g., "show me the diagram image"), **When** the router evaluates it, **Then** the routing decision targets the image-capable retrieval source
3. **Given** a general informational query with no visual keywords, **When** the router evaluates it, **Then** the routing decision targets both text and image sources in parallel
4. **Given** any query, **When** the router produces a decision, **Then** the decision includes the retrieval mode and a confidence score ≥ 0

---

### User Story 2 - Parallel Multi-Source Document Retrieval (Priority: P1)

As a RAG system, I want to retrieve relevant passages from multiple document sources simultaneously so that I minimize latency while maximizing coverage across text and image content.

The system queries all applicable sources in parallel, collects results as they become available, merges them into a unified ranked list, and gracefully degrades when any single source is slow or unavailable.

**Why this priority**: Multi-source parallel retrieval directly determines the quality and speed of the final response. Failing to parallelize increases latency multiplicatively; failing to degrade gracefully on timeout makes the entire pipeline fragile.

**Independent Test**: Fully testable by mocking each retrieval source independently, verifying merged sorted results, and simulating a timeout on one source to confirm others are not blocked.

**Acceptance Scenarios**:

1. **Given** all retrieval sources are available, **When** a query is executed, **Then** results from all activated sources are merged into a single list ordered by descending relevance score
2. **Given** one retrieval source exceeds its configured response time limit, **When** parallel retrieval completes, **Then** results from the available sources are returned without waiting for the slow source and without raising an error
3. **Given** a query routed to both text and image sources, **When** both sources return results, **Then** the merged list contains entries from both sources fused using weighted RRF (text weight higher than image weight)
4. **Given** a retrieval source returns results, **When** scores are evaluated, **Then** only results meeting the minimum score threshold are included in the output

---

### User Story 3 - Reranking, Deduplication, and Context Injection (Priority: P2)

As a RAG system, I want to reorder candidate passages by query–passage relevance, remove near-identical content, and inject a clean token-bounded context into the final prompt so that the language model receives only the most pertinent non-redundant information.

**Why this priority**: Reranking improves precision by re-scoring passages with stronger relevance signals. Deduplication prevents the language model from being confused by repeated content. Context injection must respect prompt limits — exceeding them leads to truncation failures or degraded responses.

**Independent Test**: Fully testable by providing a ranked candidate list and verifying that: (a) the reranker produces a different order with the best passage first; (b) near-duplicate passages are removed; (c) the injected prompt does not exceed the configured token budget.

**Acceptance Scenarios**:

1. **Given** a set of 5 candidate passages with initial retrieval scores, **When** the reranker evaluates each passage against the query, **Then** passages are reordered so the passage most semantically relevant to the query appears first
2. **Given** two passages with near-identical content (similarity above the deduplication threshold), **When** the aggregator processes them, **Then** only one passage is retained in the output
3. **Given** a large number of retrieved passages, **When** context is injected into the prompt, **Then** the final prompt does not exceed the configured maximum token budget
4. **Given** the full retrieval and reranking pipeline completes, **When** the injected prompt is generated, **Then** it contains the user query, the ranked retrieved passages with source citations

---

### Edge Cases

- When all retrieval sources return zero results, the system returns an empty context to the caller without error; the caller (generation layer) is responsible for handling the no-results case gracefully (e.g., generating a "no relevant content found" response).
- How does the system handle a query that matches only image content when the image retriever is unavailable?
- What happens when the reranker scores all passages below the minimum threshold?
- How does the system behave if the merged results exceed the token limit before deduplication?
- What happens when two passages are perfectly identical (similarity = 1.0)?
- How does the system handle an extremely long query that approaches input length limits?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expand each user query into exactly 5 variants (the original plus 4 reformulations using synonyms, related terms, and temporal context) to increase retrieval recall
- **FR-002**: The system MUST classify each query's intent and determine which retrieval sources to activate (text, image, or both)
- **FR-003**: The routing decision MUST include the selected retrieval mode and a confidence score
- **FR-004**: The system MUST execute all activated retrieval sources in parallel, not sequentially
- **FR-005**: Each retrieval source MUST have a configurable response time limit; results from a source that exceeds this limit MUST be discarded without blocking other sources
- **FR-006**: Text-based retrieval MUST return up to a configurable maximum of passages (default: top-20) sorted by descending semantic similarity score, including only passages above the configured minimum score threshold
- **FR-007**: Sparse keyword-based retrieval MUST operate independently from vector retrieval and return up to a configurable maximum of passages (default: top-10) in its own ranked list
- **FR-008**: Image-based retrieval MUST search across document images and return up to a configurable maximum of results (default: top-5) above the configured minimum score threshold
- **FR-009**: Results from all activated retrieval sources MUST be merged using weighted Reciprocal Rank Fusion (RRF, k=60) with source weights: text 0.5, image 0.3, BM25 0.2; the output is a single unified ranked list ordered by descending fused score
- **FR-010**: The system MUST reorder candidate passages by computing a relevance score for each query–passage pair, with the highest-scoring passage first in the output
- **FR-011**: The system MUST deduplicate passages whose content similarity exceeds the configured threshold, retaining only the highest-scoring entry among near-duplicates
- **FR-012**: The system MUST inject the re-ranked deduplicated passages into the final prompt as structured context with source citations
- **FR-013**: The injected prompt MUST NOT exceed the configured maximum token budget
- **FR-014**: The final prompt MUST contain both the user query and the injected context in a form ready for the language model
- **FR-015**: When all retrieval sources return zero results, the system MUST return an empty context to the caller without raising an error; no fallback threshold relaxation or unconstrained generation is permitted
- **FR-016**: The system MUST emit observable metrics for each retrieval source individually (execution latency, number of results returned) and for the reranking stage (duration, number of passages in and out)

### Key Entities

- **Query**: The user's input question as raw text; expanded into exactly 5 variants (original + 4 reformulations) before retrieval
- **Routing Decision**: The result of query analysis — which retrieval sources to activate and the confidence level of that decision
- **Retrieval Result**: A single document passage from one source, carrying a relevance score, source identifier, and text content
- **Merged Result Set**: The combined, RRF-fused (k=60, weights text:0.5 / image:0.3 / BM25:0.2), and deduplicated collection of retrieval results from all activated sources, ordered by descending fused score
- **Injected Prompt**: The final prompt for the language model — user query augmented with ranked retrieved context and citations

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 80% of unit tests covering all retrieval components pass on first run, with no single test exceeding 500 ms execution time
- **SC-002**: The parallel retrieval pipeline returns results within 3 seconds under normal operating conditions even when multiple sources are queried simultaneously
- **SC-003**: When one retrieval source times out, the system returns partial results from the remaining sources within the configured timeout window without error
- **SC-004**: After reranking, the passage with the highest query–passage relevance score appears first in 100% of evaluated test cases
- **SC-005**: Near-duplicate passages above the configured similarity threshold are eliminated in 100% of cases, with no duplicate content reaching the injected prompt
- **SC-006**: The injected prompt never exceeds the configured token budget in any test scenario
- **SC-007**: Test coverage for the retrieval module reaches ≥ 80% of lines and branches
- **SC-008**: Per-retriever latency, per-source result count, and reranking duration are observable and verifiable in the metrics output for every retrieval execution

## Clarifications

### Session 2026-03-28

- Q: When all retrieval sources return zero results, what should the system do? → A: Return an empty context to the caller (Option A); no threshold relaxation or unconstrained generation.
- Q: What are the default maximum result counts per retrieval source before reranking? → A: Text top-20, Image top-5, BM25 top-10 (configurable defaults per project architecture).
- Q: How many query variants should the query transformer generate per user query? → A: Exactly 5 variants (original + 4 reformulations with synonyms and temporal context).
- Q: Should the retrieval pipeline emit observable metrics? → A: Yes — per-retriever latency, result count per source, and total reranking duration (Option A).
- Q: Should multi-source result merging use weighted fusion or equal-weight merging? → A: Weighted RRF fusion (k=60, text:0.5 / image:0.3 / BM25:0.2) per project architecture (Option A).

## Assumptions

- The retrieval pipeline is an internal system component; users interact with it indirectly through the conversational chat interface
- The vector stores and sparse index are already populated with document embeddings prior to retrieval; this phase covers retrieval only, not ingestion
- The language model that consumes the injected prompt is outside the scope of this specification
- Retrieval source configurations (score thresholds, top-K limits, timeouts, token budget) are fixed in application configuration and not changeable by end users at runtime
- The deduplication similarity threshold and maximum token budget are pre-configured values
- Unit tests use mocked retrieval sources; no real vector stores or external services are contacted during testing
- The cross-encoder model used for reranking is pre-loaded and available locally, requiring no external network call during unit tests
- The ingestion pipeline (Phases 1 and 2) is already complete and its outputs are available as the retrieval data source
