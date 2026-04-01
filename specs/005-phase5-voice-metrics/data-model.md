# Data Model: Phase 5 — Voice Transcription & RAG Observability

**Feature**: `005-phase5-voice-metrics`  
**Date**: 2026-04-01

---

## Entity 1 — AudioTranscription (Value Object, Stateless)

Represents a single transcription request lifecycle. Not persisted.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `audioBytes` | `byte[]` | NOT NULL, length ∈ [1, 26_214_400] | Max 25 MB (25 × 1024 × 1024 bytes) |
| `originalFilename` | `String` | NOT NULL | Used to resolve file extension |
| `language` | `String` | Nullable | ISO 639-1 code ("fr", "en"); null → auto-detect |
| `transcript` | `String` (output) | NOT NULL, not blank, trimmed | Returned to caller; never stored or logged in full |

**Validation rules**:
- `audioBytes == null || audioBytes.length == 0` → `IllegalArgumentException("Données audio vides ou absentes")`
- `audioBytes.length > 26_214_400` → `IllegalArgumentException` (25 MB exceeded)
- `audioBytes.length < minAudioBytes` (from `WhisperProperties`) → warn but continue
- `transcript == null || transcript.isBlank()` → `RuntimeException` (no speech detected)

**State transitions**:
```
RECEIVED → VALIDATED → TEMP_FILE_CREATED → API_CALLED → TRANSCRIPT_VALIDATED → RETURNED
                ↓                                ↓
          REJECTED (invalid)          FAILED (API error / blank result)
```
In all terminal states, the temp file is deleted.

**Privacy**: `audioBytes` content MUST NOT appear in logs. `transcript` MUST be logged as `[N chars]` only.

---

## Entity 2 — PipelineMetric (Micrometer Meter, In-Memory)

Represents a named measurement registered in the `MeterRegistry`. Not persisted — scraped by Prometheus at intervals.

| Meter Name | Type | Tags | Description |
|---|---|---|---|
| `rag_ingestion_files_total` | Counter | `strategy`, `status` (success/error) | Files ingested |
| `rag_ingestion_duration_seconds` | Timer | `strategy` | Ingestion wall time |
| `rag_ingestion_errors_total` | Counter | `strategy`, `error_type` | Ingestion failures |
| `rag_embeddings_total` | Counter | `strategy` | Embeddings created |
| `rag_duplicates_total` | Counter | `strategy` | Duplicate files detected |
| `rag_viruses_detected_total` | Counter | `virus` | Virus detections |
| `rag_cache_hits_total` | Counter | `cache` | Cache hits by scope |
| `rag_cache_misses_total` | Counter | `cache` | Cache misses by scope |
| `rag_api_calls_total` | Counter | `service`, `operation` | External API calls |
| `rag_api_errors_total` | Counter | `service`, `operation` | External API errors |
| `rag_api_duration_seconds` | Timer | `service`, `operation` | API call latency |
| `rag_generation_duration_seconds` | Timer | — | LLM generation latency |
| `rag_tokens_generated_total` | Counter | — | Token accumulator |
| `rag_retrieval_duration_seconds` | Timer | `retriever` | Retrieval latency per source |
| `rag_reranking_duration` | Timer | `strategy` (optional) | Reranking latency |
| `rag_query_transformation_duration_seconds` | Timer | — | Query transformation latency |
| `rag_pipeline_duration_seconds` | Timer | — | End-to-end pipeline latency |
| `rag_active_ingestions` | Gauge | `component=ingestion` | Current active ingestions |
| `rag_active_queries` | Gauge | `component=retrieval` | Current active queries |
| `rag_total_files` | Gauge | `component=ingestion` | Total files processed (lifetime) |
| `rag_total_queries` | Gauge | `component=retrieval` | Total queries processed (lifetime) |
| `rag_total_tokens` | Gauge | `component=generation` | Total tokens generated (lifetime) |

**Counter cache invariant**: meters are registered once via `ConcurrentHashMap` key `name|tag1|tag2|...`; no re-registration on subsequent calls.

**Gauge backing**: atomic primitives (`AtomicInteger`, `AtomicLong`, `AtomicReference<Double>`) — thread-safe without external locking.

---

## Entity 3 — CacheEvent (Conceptual, No Storage)

Represents a single embedding cache lookup outcome. Captured as an increment to either `rag_cache_hits_total` or `rag_cache_misses_total` — mutually exclusive per lookup.

| Field | Values | Notes |
|---|---|---|
| `cacheType` | `"embedding"` (primary scope) | Tag value in counter |
| `outcome` | `HIT` \| `MISS` | Determines which counter is incremented |

**Invariant**: One lookup → exactly one counter incremented (hit XOR miss, never both).

---

## Entity 4 — TempAudioFile (Transient, File-System)

A short-lived file created during transcription processing.

| Attribute | Value |
|---|---|
| Name pattern | `whisper_<UUID><extension>` |
| Location | `System.getProperty("java.io.tmpdir")` |
| Extension | Resolved from `originalFilename`; default `.webm` if absent |
| Lifetime | Created before API call; deleted in `finally` block regardless of outcome |

**Invariant**: For every `create()` call, `deleteSilently()` MUST be called exactly once.

---

## Dependency Graph

```
VoiceController
    └─► VoiceFacade
            ├─► WhisperService
            │       ├─► WhisperProperties  (model, timeout, minAudioBytes, defaultExtension)
            │       ├─► AudioTempFile      (create / deleteSilently)
            │       └─► OpenAiService      (created in @PostConstruct from apiKey)
            └─► RateLimitService           (checkVoiceLimit — 10/min per user)

OpenAiEmbeddingService
    ├─► EmbeddingModel   (LangChain4j — embed / embedAll)
    └─► RAGMetrics       (recordApiCall / recordApiError)

RAGMetrics
    └─► MeterRegistry    (SimpleMeterRegistry in tests, PrometheusMeterRegistry in prod)
```
