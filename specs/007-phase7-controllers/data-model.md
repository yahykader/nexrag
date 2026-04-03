# Data Model & Mock Contracts: Phase 7 — Controllers Unit Tests (MockMvc)

**Branch**: `007-phase7-controllers` | **Date**: 2026-04-02

## Overview

Phase 7 has no new persistent data model. The "data model" for this phase is the set of
**mock contracts** — the stubbed return values and thrown exceptions that `@MockBean` instances
must satisfy in each `@WebMvcTest` slice. This document also captures the `@TestConfiguration`
blueprint for `MetricsController` and the required production code change to `StreamingRequest`.

---

## Production Code Change — StreamingRequest

**File**: `nex-rag/src/main/java/com/exemple/nexrag/service/rag/streaming/model/StreamingRequest.java`

**Change required** (Task T0 — prerequisite for Task T2):

| Field | Current state | Required state |
|---|---|---|
| `query` | `private String query;` | `@NotBlank private String query;` |

The `streamPost()` method in `StreamingAssistantController` must also add `@Valid` to the
`@RequestBody` parameter:

```
// Before
public SseEmitter streamPost(@RequestBody StreamingRequest request)

// After
public SseEmitter streamPost(@Valid @RequestBody StreamingRequest request)
```

Import required: `jakarta.validation.Valid`, `jakarta.validation.constraints.NotBlank`

---

## Mock Contracts by Controller

### T1 — MultimodalIngestionControllerSpec

**Dependency mocked**: `IngestionFacade` (`@MockBean`)  
**Advice imported**: `IngestionExceptionHandler`

| Scenario | Mock setup | Expected HTTP |
|---|---|---|
| Sync upload — success | `ingestionFacade.uploadSync(any(), any())` → `IngestionResponse` (success=true) | 200 |
| Async upload — duplicate | `ingestionFacade.uploadAsync(any(), any())` → `AsyncResponse` (duplicate=true) | 409 |
| Async upload — accepted | `ingestionFacade.uploadAsync(any(), any())` → `AsyncResponse` (duplicate=false) | 202 |
| Batch upload | `ingestionFacade.uploadBatch(any(), any())` → `BatchResponse` | 202 |
| Batch detailed upload | `ingestionFacade.uploadBatchDetailed(any(), any())` → `BatchDetailedResponse` | 202 |
| Status check | `ingestionFacade.getStatus("batch-123")` → `StatusResponse` | 200 |
| Rollback | `ingestionFacade.rollback("batch-123")` → `RollbackResponse` | 200 |
| Health detailed — healthy | `ingestionFacade.getDetailedHealth()` → `DetailedHealthResponse` (healthy=true) | 200 |
| Health detailed — unhealthy | `ingestionFacade.getDetailedHealth()` → `DetailedHealthResponse` (healthy=false) | 503 |
| Health basic | _(no facade call — static map)_ | 200 |
| Active ingestions | `ingestionFacade.getActiveIngestions()` → `ActiveIngestionsResponse` | 200 |
| Stats | `ingestionFacade.getStats()` → `StatsResponse` | 200 |
| Strategies | `ingestionFacade.getStrategies()` → `StrategiesResponse` | 200 |
| DuplicateFileException | `ingestionFacade.uploadSync(any(), any())` → throws `DuplicateFileException` | 409 |
| ResourceNotFoundException | `ingestionFacade.getStatus("unknown")` → throws `ResourceNotFoundException` | 404 |
| Missing file param | _(no mock — Spring MVC binding error)_ | 400 |

---

### T2 — StreamingAssistantControllerSpec

**Dependency mocked**: `StreamingFacade` (`@MockBean`)  
**Advice imported**: none

| Scenario | Mock setup | Expected HTTP / Content-Type |
|---|---|---|
| GET stream | `streamingFacade.startStream(any())` → `new SseEmitter()` | 200, `text/event-stream` |
| POST stream | `streamingFacade.startStream(any())` → `new SseEmitter()` | 200, `text/event-stream` |
| Cancel stream | `streamingFacade.cancelStream("session-1")` → _(void)_ | 200 (empty body) |
| Health | _(no facade call)_ | 200 |
| POST — empty query | _(no mock — bean validation rejects before facade)_ | 400 |

---

### T3 — MultimodalCrudControllerSpec

**Dependency mocked**: `CrudFacade` (`@MockBean`)  
**Advice imported**: `CrudExceptionHandler`  
**Package**: `com.exemple.nexrag.controller` (not `service.rag.controller`)

| Scenario | Mock setup | Expected HTTP |
|---|---|---|
| Delete by ID | `crudFacade.deleteById("emb-1", TEXT)` → `DeleteResponse` (success=true) | 200 |
| Delete batch by batchId | `crudFacade.deleteBatchById("batch-1")` → `DeleteResponse` | 200 |
| Delete text batch (list) | `crudFacade.deleteBatch(anyList(), TEXT)` → `DeleteResponse` | 200 |
| Delete image batch (list) | `crudFacade.deleteBatch(anyList(), IMAGE)` → `DeleteResponse` | 200 |
| Delete all — confirmed | `crudFacade.deleteAll("DELETE_ALL_FILES")` → `DeleteResponse` | 200 |
| Check duplicate | `crudFacade.checkDuplicate(any())` → `DuplicateCheckResponse` | 200 |
| Get batch info | `crudFacade.getBatchInfo("batch-1")` → `BatchInfoResponse` | 200 |
| System stats | `crudFacade.getSystemStats()` → `SystemStatsResponse` | 200 |
| ResourceNotFoundException | `crudFacade.deleteById("unknown", TEXT)` → throws `ResourceNotFoundException` | 404 |
| Missing confirmation param | _(no mock — Spring MVC missing required param)_ | 400 |

---

### T4 — MetricsControllerSpec

**Dependency**: `PrometheusMeterRegistry` (injected via `@TestConfiguration`, **not** `@MockBean`)  
**Advice imported**: none

```java
@TestConfiguration
static class TestMetricsConfig {
    @Bean
    PrometheusMeterRegistry prometheusMeterRegistry() {
        PrometheusMeterRegistry registry =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.timer("rag.query.duration");
        registry.counter("rag.queries.total");
        registry.counter("rag.queries.success");
        AtomicLong activeConns = new AtomicLong(0);
        registry.gauge("rag.connections.active", activeConns, AtomicLong::get);
        return registry;
    }
}
```

| Scenario | Expected HTTP | Expected Content-Type / Body |
|---|---|---|
| GET /prometheus | 200 | `text/plain`, non-empty scrape string |
| GET /health | 200 | JSON with fields `status`, `application` |
| GET /metrics/summary | 200 | JSON with keys `queries`, `performance`, `connections` |

---

### T5 — VoiceControllerSpec

**Dependency mocked**: `VoiceFacade` (`@MockBean`)  
**Advice imported**: `VoiceExceptionHandler`

| Scenario | Mock setup | Expected HTTP |
|---|---|---|
| Transcribe — success | `voiceFacade.transcribe(any(), any())` → `TranscriptionResponse` (success=true, transcript="Hello") | 200 |
| Transcribe — language param forwarded | `voiceFacade.transcribe(any(), eq("en"))` → `TranscriptionResponse` | 200 |
| Transcribe — default language | `voiceFacade.transcribe(any(), eq(VoiceConstants.DEFAULT_LANGUAGE))` → `TranscriptionResponse` | 200 |
| Health | `voiceFacade.health()` → `VoiceHealthResponse` | 200 |
| IllegalArgumentException (invalid audio) | `voiceFacade.transcribe(any(), any())` → throws `IllegalArgumentException` | 400 |
| Missing audio part | _(no mock — Spring MVC missing required param)_ | 400 |

---

### T6 — WebSocketStatsControllerSpec

**Dependency mocked**: `WebSocketSessionManager` (`@MockBean`)  
**Advice imported**: none

| Scenario | Mock setup | Expected HTTP / Body |
|---|---|---|
| GET /stats | `sessionManager.getStats()` → `SessionStats(active=3, total=10)` | 200 |
| GET /active | `sessionManager.getActiveSessionCount()` → `5` | 200, body contains `activeSessions: 5` |
| GET /sessions | `sessionManager.getActiveSessionIds()` → `Set.of("s1","s2")` | 200, JSON set |
| GET /session/{id} — found | `sessionManager.getSessionInfo("s1")` → `SessionInfo(...)` | 200 |
| GET /session/{id} — not found | `sessionManager.getSessionInfo("unknown")` → `null` | 404 |
| POST /cleanup | `sessionManager.getActiveSessionCount()` → 5 (before), 3 (after) | 200, body contains `cleaned: 2`, `remaining: 3` |
| GET /health | `sessionManager.getStats()` → `SessionStats(active=2, total=8)` | 200, body contains `status: UP` |

---

## @ControllerAdvice Import Pattern

For `@WebMvcTest` slices that include an advice bean, use `@Import`:

```java
@WebMvcTest(MultimodalIngestionController.class)
@Import(IngestionExceptionHandler.class)
class MultimodalIngestionControllerSpec { ... }

@WebMvcTest(MultimodalCrudController.class)
@Import(CrudExceptionHandler.class)
class MultimodalCrudControllerSpec { ... }

@WebMvcTest(VoiceController.class)
@Import(VoiceExceptionHandler.class)
class VoiceControllerSpec { ... }
```

**Note**: `@RestControllerAdvice` beans are NOT picked up by `@WebMvcTest` component scan
automatically — `@Import` is mandatory.

---

## Key DTOs (response shapes relevant to assertions)

| DTO | Key fields asserted in tests |
|---|---|
| `IngestionResponse` | `success` (boolean), `duplicate` (boolean), `message` (string), `batchId` (string) |
| `AsyncResponse` | `duplicate` (boolean), `batchId` (string) |
| `DeleteResponse` | `success` (boolean), `message` (string) |
| `DuplicateCheckResponse` | `duplicate` (boolean) |
| `TranscriptionResponse` | `success` (boolean), `transcript` (string) |
| `SessionStats` | `activeSessions` (int), `totalSessions` (int) |
| `SessionInfo` | `sessionId` (string), connection metadata |
