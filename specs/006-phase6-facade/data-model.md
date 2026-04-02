# Data Model: Phase 6 — Facade Unit Tests

**Branch**: `006-phase6-facade` | **Date**: 2026-04-02

---

## Entities

### IngestionStatus (enum)

Represents the outcome classification of an ingestion attempt.

| Value | Meaning |
|-------|---------|
| `SUCCESS` | Document accepted, processed, and stored |
| `DUPLICATE` | Document content already present — pipeline short-circuited |
| `REJECTED` | Document blocked (virus found or antivirus unavailable) |

**State transitions** (IngestionFacadeImpl pipeline):
```
START
  │
  ▼
FileValidator.validate()
  │── invalid ──► REJECTED (validation reason)
  ▼
AntivirusGuard.scan()
  │── FOUND ────► REJECTED (virus reason)
  │── UNAVAILABLE► REJECTED ("ANTIVIRUS_UNAVAILABLE")
  ▼
DuplicateChecker.isDuplicate()
  │── true ─────► DUPLICATE (no orchestrator call)
  ▼
IngestionOrchestrator.ingest()
  └────────────► SUCCESS (batchId)
```

---

### IngestionResult

Carries the outcome of `IngestionFacadeImpl.ingest()`.

| Field | Type | Notes |
|-------|------|-------|
| `status` | `IngestionStatus` | Mandatory |
| `batchId` | `String` | Present when `status == SUCCESS` |
| `reason` | `String` | Present when `status == REJECTED`; e.g. `"ANTIVIRUS_UNAVAILABLE"`, `"VIRUS_FOUND"` |

**Invariant**: `batchId` and `reason` are mutually exclusive — exactly one is populated for any non-DUPLICATE result.

---

### DocumentPage

Paginated view returned by `CrudFacadeImpl.listDocuments()`.

| Field | Type | Notes |
|-------|------|-------|
| `items` | `List<DocumentSummary>` | Items for the current page; size ≤ `pageSize` |
| `totalElements` | `long` | Total count across all pages |
| `pageNumber` | `int` | Zero-based current page index |
| `pageSize` | `int` | Requested page size |

**Invariant**: `items.size() ≤ pageSize`; on the last page `items.size()` may be < `pageSize`.  
**Edge case**: Page beyond last page → `items` is empty, `totalElements` unchanged (not an error).

---

### StreamingEvent

A single event emitted in the `Flux<StreamingEvent>` returned by `StreamingFacadeImpl.stream()`.

| Field | Type | Notes |
|-------|------|-------|
| `type` | `EventType` | `CONTENT`, `METADATA`, or `ERROR` |
| `payload` | `String` | Token fragment (CONTENT), JSON metadata (METADATA), or error message (ERROR) |

**EventType enum**:

| Value | Meaning |
|-------|---------|
| `CONTENT` | An LLM token fragment |
| `METADATA` | Retrieval metadata (citations, scores) |
| `ERROR` | Orchestration error converted by the facade |

**Invariant**: When `type == ERROR`, the `Flux` completes after this event (no further items). When the orchestrator returns `Flux.empty()`, the facade's `Flux` completes with zero items — this is a valid success state, not an error.

---

### VoiceTranscriptionResult

Outcome of `VoiceFacadeImpl.transcribe()`.

| Field | Type | Notes |
|-------|------|-------|
| `transcription` | `String` | Non-empty, trimmed — present on success |
| `errorReason` | `String` | Error description — present on failure |
| `success` | `boolean` | `true` if transcription available; `false` if error |

**Invariant**: `transcription` is populated iff `success == true`; `errorReason` is populated iff `success == false`.

**Error reasons**:

| Reason | Trigger |
|--------|---------|
| `"INVALID_AUDIO"` | Audio bytes are null or zero-length |
| `"SERVICE_UNAVAILABLE"` | `WhisperService` is unreachable or unavailable |

---

### DuplicateChecker (component behaviour contract)

Not a data entity — a shared lookup component.

| Input | Store State | Output |
|-------|-------------|--------|
| Known content hash | Store reachable | `true` |
| Unknown content hash | Store reachable | `false` |
| Any hash | Store unreachable | `true` (fail closed) |

**Safety requirement** (Constitution Principle IV): 100 % branch coverage — all three rows above must be exercised in `DuplicateCheckerSpec`.

---

## Mock Contracts

Each mock used in Phase 6 specs must honour the following behavioural contracts (Constitution Principle II — LSP):

| Mock | Method | Stub returns | Exception case |
|------|--------|-------------|----------------|
| `FileValidator` | `validate(file)` | `void` (no-op on pass) | throws `InvalidFileTypeException` or `FileSizeExceededException` |
| `AntivirusGuard` | `scan(file)` | `void` (no-op on pass) | throws `VirusFoundException` or `AntivirusUnavailableException` |
| `DeduplicationService` | `isDuplicate(hash)` | `true` / `false` | throws `DeduplicationStoreException` |
| `IngestionOrchestrator` | `ingest(request)` | `IngestionResult("batchId")` | throws `RuntimeException` |
| `EmbeddingStoreDeleter` | `delete(documentId)` | `void` | throws `DocumentNotFoundException` |
| `StreamingOrchestrator` | `stream(query)` | `Flux.just(event)` / `Flux.empty()` | `Flux.error(new RuntimeException(...))` |
| `WhisperService` | `transcribeAudio(bytes)` | `"transcription text"` | throws `IllegalArgumentException` (empty) |
| `WhisperService` | `isAvailable()` | `true` / `false` | — |
