# Feature Specification: Phase 7 — Controllers Unit Tests (MockMvc)

**Feature Branch**: `007-phase7-controllers`  
**Created**: 2026-04-02  
**Status**: Draft  
**Input**: User description: "read nexrag-test-plan-speckit.md and create a specification for the PHASE 7 — Controllers"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Multimodal Ingestion Controller (Priority: P1)

As an API client, I want to upload files via REST so that they are ingested into the RAG knowledge base. The ingestion controller exposes synchronous upload, asynchronous upload, batch upload, ingestion status tracking, rollback, and operational monitoring endpoints — all delegating to the ingestion facade without any business logic in the controller itself.

**Why this priority**: Ingestion is the gateway for all knowledge base content. Without verified HTTP routing for the ingestion controller, no data can enter the RAG pipeline. It is the highest-value controller to validate first.

**Independent Test**: Can be fully tested with a MockMvc test slice (`@WebMvcTest`) by mocking the `IngestionFacade` and verifying HTTP status codes, response bodies, and Content-Type headers for each endpoint — without starting a full application context or any real infrastructure.

**Acceptance Scenarios**:

1. **Given** a valid PDF file is submitted via `POST /api/v1/ingestion/upload`, **When** the ingestion facade returns a success response, **Then** the controller returns HTTP 200 with the ingestion response body.
2. **Given** a valid file is submitted via `POST /api/v1/ingestion/upload/async` and the facade signals a duplicate, **When** the controller evaluates the response, **Then** it returns HTTP 409 CONFLICT.
3. **Given** a valid file is submitted via `POST /api/v1/ingestion/upload/async` and the facade signals no duplicate, **When** the controller evaluates the response, **Then** it returns HTTP 202 ACCEPTED.
4. **Given** multiple files are submitted via `POST /api/v1/ingestion/upload/batch`, **When** the facade accepts them, **Then** the controller returns HTTP 202 ACCEPTED with a batch response body.
5. **Given** a batch identifier is provided to `GET /api/v1/ingestion/status/{batchId}`, **When** the facade returns the status, **Then** the controller returns HTTP 200 with the status response body.
6. **Given** a batch identifier is provided to `DELETE /api/v1/ingestion/rollback/{batchId}`, **When** the facade returns the rollback result, **Then** the controller returns HTTP 200 with the rollback response body.
7. **Given** the health check endpoint `GET /api/v1/ingestion/health/detailed` is called and the facade reports an unhealthy state, **When** the controller evaluates the response, **Then** it returns HTTP 503 SERVICE UNAVAILABLE.
8. **Given** the health check endpoint `GET /api/v1/ingestion/health/detailed` is called and the facade reports a healthy state, **When** the controller evaluates the response, **Then** it returns HTTP 200.
9. **Given** `GET /api/v1/ingestion/health` is called, **When** the controller handles the request, **Then** it returns HTTP 200 with fields `status`, `service`, `streaming`, `duplicateDetection`, and `websocketProgress`.
10. **Given** `GET /api/v1/ingestion/active` is called, **When** the facade returns the active ingestions list, **Then** the controller returns HTTP 200 with an active ingestions response body.
11. **Given** `GET /api/v1/ingestion/stats` is called, **When** the facade returns global statistics, **Then** the controller returns HTTP 200 with a stats response body.
12. **Given** `GET /api/v1/ingestion/strategies` is called, **When** the facade returns the available strategy list, **Then** the controller returns HTTP 200 with a strategies response body.
13. **Given** multiple files are submitted via `POST /api/v1/ingestion/upload/batch/detailed`, **When** the facade returns per-file results, **Then** the controller returns HTTP 202 ACCEPTED with a detailed batch response body.

---

### User Story 2 - Streaming Assistant Controller (Priority: P2)

As an API client, I want to query the RAG assistant in SSE streaming mode so that I receive the answer token by token as it is generated. The streaming controller exposes GET and POST stream endpoints, a cancellation endpoint, and a health check — all delegating to the streaming facade.

**Why this priority**: Streaming is the primary end-user interaction path. Verifying that HTTP routing delivers an SSE response and correctly maps facade calls to stream emissions is essential before the system can be demonstrated.

**Independent Test**: Can be fully tested with `@WebMvcTest` by mocking `StreamingFacade`. Assertions cover `Content-Type: text/event-stream`, cancellation returning HTTP 200, and health returning a plain-text string.

**Acceptance Scenarios**:

1. **Given** a query string is submitted via `GET /api/v1/assistant/stream`, **When** the facade starts the stream, **Then** the response `Content-Type` is `text/event-stream`.
2. **Given** a JSON body is submitted via `POST /api/v1/assistant/stream`, **When** the facade starts the stream, **Then** the response `Content-Type` is `text/event-stream`.
3. **Given** a session identifier is submitted via `POST /api/v1/assistant/stream/{sessionId}/cancel`, **When** the facade cancels the stream, **Then** the controller returns HTTP 200 with an empty body.
4. **Given** `GET /api/v1/assistant/stream/health` is called, **When** the controller handles the request, **Then** it returns HTTP 200 with a non-empty health message.
5. **Given** a POST body with an empty `query` field is submitted to `POST /api/v1/assistant/stream`, **When** Spring MVC validates the request, **Then** the controller returns HTTP 400 BAD REQUEST and the streaming facade is never called.

---

### User Story 3 - CRUD Controller for Embedding Management (Priority: P3)

As an operator, I want to delete embeddings (individually, by batch, or globally) and check for duplicates through a REST API so that the knowledge base content can be maintained and managed without coupling management tools to internal persistence details.

**Why this priority**: Document management is needed to maintain knowledge base quality. It depends on the ingestion controller being validated first, but is equally important for operational readiness.

**Independent Test**: Can be fully tested with `@WebMvcTest` by mocking `CrudFacade`. Assertions cover HTTP status codes and response body shape for delete operations, duplicate checks, batch info retrieval, and system stats.

**Acceptance Scenarios**:

1. **Given** an embedding ID and type are submitted to `DELETE /api/v1/crud/file/{embeddingId}`, **When** the facade deletes the embedding, **Then** the controller returns HTTP 200 with a delete response body.
2. **Given** a batch ID is submitted to `DELETE /api/v1/crud/batch/{batchId}/files`, **When** the facade deletes the batch, **Then** the controller returns HTTP 200.
3. **Given** a list of text embedding IDs is submitted to `DELETE /api/v1/crud/files/text/batch`, **When** the facade processes the deletion, **Then** the controller returns HTTP 200.
4. **Given** a confirmation parameter `DELETE_ALL_FILES` is submitted to `DELETE /api/v1/crud/files/all`, **When** the facade executes the global deletion, **Then** the controller returns HTTP 200.
5. **Given** a file is submitted to `POST /api/v1/crud/check-duplicate`, **When** the facade checks for a duplicate, **Then** the controller returns HTTP 200 with a duplicate check response.
6. **Given** a batch ID is submitted to `GET /api/v1/crud/batch/{batchId}/info`, **When** the facade retrieves the batch info, **Then** the controller returns HTTP 200 with batch details.
7. **Given** `GET /api/v1/crud/stats/system` is called, **When** the facade returns system stats, **Then** the controller returns HTTP 200 with stats data.

---

### User Story 4 - Voice Transcription Controller (Priority: P3)

As an API client, I want to submit an audio file for transcription via REST so that I can drive RAG queries with voice input. The voice controller accepts audio files with an optional language code and delegates entirely to the voice facade.

**Why this priority**: Voice input is a direct user-facing feature. Validating that the controller correctly routes the audio file and language parameter to the facade is required before end-to-end voice-to-RAG queries can work.

**Independent Test**: Can be fully tested with `@WebMvcTest` by mocking `VoiceFacade`. Assertions cover HTTP status, the transcription response body, and the health endpoint response.

**Acceptance Scenarios**:

1. **Given** a valid audio file is submitted to `POST /api/v1/voice/transcribe`, **When** the facade returns a transcription response, **Then** the controller returns HTTP 200 with the transcription result.
2. **Given** a language code `en` is provided as a query parameter alongside the audio file, **When** the facade is called, **Then** the facade receives the language code `en`.
3. **Given** no language code is provided, **When** the controller processes the request, **Then** the facade is called with the default language code.
4. **Given** `GET /api/v1/voice/health` is called, **When** the facade returns a health response, **Then** the controller returns HTTP 200.

---

### User Story 5 - Metrics and Actuator Controller (Priority: P4)

As an operations engineer, I want to access Prometheus-format metrics and a human-readable metrics summary via dedicated endpoints so that I can integrate the RAG platform into Prometheus/Grafana dashboards and observe real-time performance.

**Why this priority**: Observability is critical for production readiness but does not block functional testing. It is lower priority than the primary data-path controllers.

**Independent Test**: Can be fully tested with `@WebMvcTest` plus a `@TestConfiguration` that provides a pre-loaded `PrometheusMeterRegistry` bean. Assertions cover `Content-Type: text/plain` for the Prometheus endpoint, HTTP 200 for health and summary, and the presence of known metric keys in the summary response. No facade mock is required.

**Acceptance Scenarios**:

1. **Given** `GET /api/actuator/prometheus` is called, **When** the controller scrapes the registry, **Then** the response `Content-Type` is `text/plain` and the body is non-empty.
2. **Given** `GET /api/actuator/health` is called, **When** the controller builds the health object, **Then** the response contains the fields `status` and `application`.
3. **Given** `GET /api/actuator/metrics/summary` is called and the required metrics exist in the registry, **When** the controller aggregates the summary, **Then** the response contains keys `queries`, `performance`, and `connections` with numeric values ≥ 0.

---

### User Story 6 - WebSocket Session Statistics Controller (Priority: P4)

As an operations engineer, I want to monitor active WebSocket sessions and trigger cleanup of stale sessions via REST so that I can observe real-time connectivity load and maintain a healthy connection pool.

**Why this priority**: WebSocket monitoring is an operational concern. It is lower priority than the primary data-path controllers but required for production observability.

**Independent Test**: Can be fully tested with `@WebMvcTest` by mocking `WebSocketSessionManager`. Assertions cover stats response shape, active session count, session list, per-session info (including not-found case), cleanup result, and health endpoint.

**Acceptance Scenarios**:

1. **Given** `GET /api/v1/websocket/stats` is called, **When** the session manager returns stats, **Then** the controller returns HTTP 200 with a stats body.
2. **Given** `GET /api/v1/websocket/active` is called, **When** the manager returns the count, **Then** the response contains the field `activeSessions` with a non-negative integer.
3. **Given** `GET /api/v1/websocket/sessions` is called, **When** the manager returns the session IDs, **Then** the response is a JSON set (may be empty).
4. **Given** a valid session ID is provided to `GET /api/v1/websocket/session/{sessionId}`, **When** the session exists, **Then** the controller returns HTTP 200 with session details.
5. **Given** an unknown session ID is provided to `GET /api/v1/websocket/session/{sessionId}`, **When** the manager returns null, **Then** the controller returns HTTP 404.
6. **Given** `POST /api/v1/websocket/cleanup` is called with an optional threshold, **When** the manager performs cleanup, **Then** the response contains the fields `cleaned` and `remaining`.
7. **Given** `GET /api/v1/websocket/health` is called, **When** the controller builds the response, **Then** the response contains the fields `status`, `activeSessions`, and `totalSessions`.

---

### Edge Cases

- Submitting `POST /api/v1/ingestion/upload` without a `file` parameter returns HTTP 400 BAD REQUEST (Spring MVC missing-parameter handling, no facade call).
- Submitting `POST /api/v1/assistant/stream` with an empty or null `query` field returns HTTP 400 BAD REQUEST via Spring MVC bean validation (`@NotBlank` on `StreamingRequest.query`); the facade is never invoked for this case and requires no stub in the test.
- Calling `DELETE /api/v1/crud/files/all` without the mandatory `confirmation` parameter returns HTTP 400.
- Calling `GET /api/v1/websocket/session/{sessionId}` for a non-existent session returns HTTP 404 with no body.
- `GET /api/actuator/metrics/summary` when a required metric has not yet been recorded — numeric values must be 0, not null, to prevent serialisation failures.
- Calling `POST /api/v1/voice/transcribe` without the `audio` file part returns HTTP 400.
- The `DELETE /api/v1/ingestion/rollback/{batchId}` endpoint must return HTTP 200 regardless of whether the batch was found — the rollback result DTO carries the outcome.
- When `IngestionFacade` throws a `DuplicateFileException`, the `@ControllerAdvice` handler converts it to HTTP 409 CONFLICT — the ingestion controller spec must include this scenario.
- When `IngestionFacade` throws a `VirusDetectedException`, the `@ControllerAdvice` handler converts it to an appropriate 4xx response — the ingestion controller spec must include this scenario.
- When `CrudFacade` throws a document-not-found exception, the `@ControllerAdvice` handler (CrudControllerAdvice) converts it to HTTP 404 — the CRUD controller spec must include this scenario.
- When `VoiceFacade` throws a transcription service exception, the `@ControllerAdvice` handler (VoiceControllerAdvice) converts it to an appropriate error response — the voice controller spec must include this scenario.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The ingestion controller MUST accept multipart file uploads at `POST /api/v1/ingestion/upload` and return HTTP 200 with the facade's synchronous response.
- **FR-002**: The ingestion controller MUST accept asynchronous uploads at `POST /api/v1/ingestion/upload/async` and return HTTP 409 when the facade signals a duplicate, HTTP 202 otherwise.
- **FR-003**: The ingestion controller MUST accept batch uploads at `POST /api/v1/ingestion/upload/batch` and `POST /api/v1/ingestion/upload/batch/detailed`, returning HTTP 202 in both cases.
- **FR-004**: The ingestion controller MUST return HTTP 503 from `GET /api/v1/ingestion/health/detailed` when the facade reports an unhealthy state, and HTTP 200 when healthy.
- **FR-005**: The ingestion controller MUST return a static health map at `GET /api/v1/ingestion/health` containing the fields `status`, `service`, `streaming`, `duplicateDetection`, and `websocketProgress`.
- **FR-006**: The streaming controller MUST return `Content-Type: text/event-stream` for both `GET /api/v1/assistant/stream` and `POST /api/v1/assistant/stream`. `StreamingRequest` MUST carry a `@NotBlank` constraint on its `query` field so that Spring MVC bean validation rejects empty or null queries with HTTP 400 before the streaming facade is invoked.
- **FR-007**: The streaming controller MUST return HTTP 200 from `POST /api/v1/assistant/stream/{sessionId}/cancel` and invoke the facade's cancellation method exactly once.
- **FR-008**: The CRUD controller MUST accept embedding deletion by ID at `DELETE /api/v1/crud/file/{embeddingId}` with a `type` parameter defaulting to `text`, and return HTTP 200.
- **FR-009**: The CRUD controller MUST require the `confirmation` query parameter for the global delete endpoint `DELETE /api/v1/crud/files/all`; omitting it MUST return HTTP 400.
- **FR-010**: The CRUD controller MUST return HTTP 200 with a duplicate check response from `POST /api/v1/crud/check-duplicate` for a valid file submission.
- **FR-011**: The voice controller MUST accept audio file uploads at `POST /api/v1/voice/transcribe` with an optional `language` parameter, defaulting to the value defined in `VoiceConstants.DEFAULT_LANGUAGE`.
- **FR-012**: The metrics controller MUST return Prometheus plain-text format from `GET /api/actuator/prometheus`.
- **FR-013**: The metrics controller MUST return a summary at `GET /api/actuator/metrics/summary` containing keys `queries`, `performance`, and `connections` with numeric values ≥ 0.
- **FR-014**: The WebSocket stats controller MUST return HTTP 404 from `GET /api/v1/websocket/session/{sessionId}` when the session manager returns null for an unknown session ID.
- **FR-015**: The WebSocket stats controller MUST return a cleanup report from `POST /api/v1/websocket/cleanup` containing the fields `cleaned`, `remaining`, and `threshold_ms`.
- **FR-016**: All controllers MUST contain zero business logic — every non-trivial computation MUST be delegated to the corresponding facade or manager. The only permitted controller-level logic is HTTP status resolution (e.g., duplicate → 409, unhealthy → 503) and log truncation.
- **FR-017**: Each `@WebMvcTest` slice for `MultimodalIngestionController`, `MultimodalCrudController`, and `VoiceController` MUST import the corresponding `@ControllerAdvice` bean (`IngestionControllerAdvice`, `CrudControllerAdvice`, `VoiceControllerAdvice`) and include at least one acceptance scenario per relevant custom exception (e.g., `DuplicateFileException → 409`, document-not-found → 404, transcription error → appropriate 4xx) to ensure exception-to-HTTP-status mapping is validated in Phase 7.

### Key Entities

- **IngestionResponse**: Outcome of a synchronous ingestion request, carrying status and batch metadata.
- **AsyncResponse**: Outcome of an asynchronous ingestion request, carrying a `duplicate` flag and batch identifier. The controller uses `duplicate` to select HTTP 409 vs. HTTP 202.
- **BatchResponse / BatchDetailedResponse**: Outcome of batch ingestion requests, carrying per-batch or per-file result details.
- **StatusResponse**: Current processing status for a given batch identifier.
- **RollbackResponse**: Outcome of a rollback operation for a given batch.
- **DeleteResponse**: Outcome of an embedding deletion operation, carrying deleted count and status.
- **DuplicateCheckResponse**: Result of a pre-ingestion duplicate check, carrying a boolean `duplicate` flag.
- **TranscriptionResponse**: Result of a voice transcription request, carrying the transcribed text or a structured error reason.
- **SessionStats / SessionInfo**: WebSocket session aggregate statistics and per-session detail, used by the WebSocket stats controller.
- **ActiveIngestionsResponse**: Snapshot of all currently running ingestion operations, returned by `GET /api/v1/ingestion/active`.
- **StatsResponse**: Global ingestion statistics (counts, durations, error rates), returned by `GET /api/v1/ingestion/stats`.
- **StrategiesResponse**: List of registered ingestion strategies and their supported document types, returned by `GET /api/v1/ingestion/strategies`.
- **DetailedHealthResponse**: Detailed health status carrying an `isHealthy` flag and component-level diagnostics, returned by `GET /api/v1/ingestion/health/detailed`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All six controller test classes (`MultimodalIngestionControllerSpec`, `StreamingAssistantControllerSpec`, `MultimodalCrudControllerSpec`, `MetricsControllerSpec`, `VoiceControllerSpec`, `WebSocketStatsControllerSpec`) pass with zero test failures.
- **SC-002**: The controller test module achieves a minimum of 80% line and branch coverage on all six controller classes as measured by the project's coverage tooling.
- **SC-003**: Each controller unit test runs in under 500 ms in isolation, with no real network, database, or Redis calls.
- **SC-004**: The ingestion controller's three HTTP status resolution paths (200 sync, 202/409 async, 503/200 detailed health) are each covered by at least one passing acceptance scenario.
- **SC-005**: The streaming controller's SSE `Content-Type` is asserted in at least one test for both the GET and POST stream variants.
- **SC-006**: The CRUD controller's not-found and missing-parameter error paths are each covered by at least one passing acceptance scenario.
- **SC-007**: The WebSocket stats controller's 404 path (unknown session) and the cleanup response fields are each covered by at least one passing acceptance scenario.
- **SC-008**: The metrics controller's Prometheus endpoint is verified to return `Content-Type: text/plain` and a non-empty body in at least one test.
- **SC-009**: No test class imports or instantiates a real facade, service, or infrastructure component — every dependency is provided as a test double.

## Assumptions

- All controllers are tested exclusively as `@WebMvcTest` slices using `MockMvc`. No full `@SpringBootTest` context is started in Phase 7; end-to-end integration is deferred to Phase 9.
- `@ControllerAdvice` beans (`IngestionControllerAdvice`, `CrudControllerAdvice`, `VoiceControllerAdvice`) are explicitly imported into their respective `@WebMvcTest` slices so that exception-to-HTTP-status mapping is covered. `StreamingAssistantController`, `MetricsController`, and `WebSocketStatsController` have no custom advice beans and require no such import.
- All facades (`IngestionFacade`, `CrudFacade`, `StreamingFacade`, `VoiceFacade`) and the `WebSocketSessionManager` are mocked using `@MockBean`. No real infrastructure is required.
- `MetricsController` does not use a facade — it injects `PrometheusMeterRegistry` directly. Its test uses `@WebMvcTest` + a `@TestConfiguration` class that creates and registers a `PrometheusMeterRegistry` pre-loaded with the four required meters (`rag.query.duration`, `rag.queries.total`, `rag.queries.success`, `rag.connections.active`). This keeps `MetricsControllerSpec` a lightweight slice consistent with all other Phase 7 test classes, with no full application context startup.
- Test classes follow the project naming convention: `[ClassUnderTest]Spec.java`, located under `src/test/java/com/exemple/nexrag/service/rag/controller/`.
- `MultimodalCrudController` is located in the package `com.exemple.nexrag.controller` (not `service.rag.controller`) per the source file; its test is placed accordingly.
- Log message assertions are not part of this phase — only HTTP routing, status codes, and response body shapes are verified.
- Spring Security filters are explicitly disabled in all `@WebMvcTest` slices (e.g., `@AutoConfigureMockMvc(addFilters=false)`) so that authentication does not interfere with controller routing tests. Authentication and rate-limiting filter behaviour is tested in Phase 8 (Interceptor & Validation).
- The `SseEmitter` returned by `StreamingFacade.startStream()` is mocked; its internal event emission is covered in Phase 6 (facade tests) and Phase 9 (integration tests).

## Clarifications

### Session 2026-04-02

- Q: Should Phase 7 controller tests verify that custom exceptions thrown by facades are converted to the correct HTTP status codes via `@ControllerAdvice`, or is this out of scope? → A: Include — add `@ControllerAdvice` beans to `@WebMvcTest` slices; each relevant controller spec adds at least one exception-mapping scenario.
- Q: Are the REST controllers protected by Spring Security authentication, and how should @WebMvcTest slices handle security? → A: Disable Security — annotate all test slices to bypass auth filters; authentication and rate-limiting filter behaviour is tested in Phase 8.
- Q: Should the 4 ingestion monitoring endpoints without acceptance scenarios (GET /active, GET /stats, GET /strategies, POST /upload/batch/detailed) be added to Phase 7 scope? → A: Add — include one acceptance scenario per endpoint in User Story 1 (scenarios 10–13).
- Q: How should `MetricsControllerSpec` provide the `PrometheusMeterRegistry` in the test context since `@WebMvcTest` does not auto-configure Micrometer? → A: `@WebMvcTest` + `@TestConfiguration` — provide a pre-loaded registry via a test config class; stays a lightweight slice consistent with all other Phase 7 tests.
- Q: Is the empty-query → HTTP 400 rule for `POST /api/v1/assistant/stream` enforced by bean validation on `StreamingRequest` or by the facade throwing an exception? → A: Bean validation — `@NotBlank` on `StreamingRequest.query` triggers Spring MVC's built-in 400 before the facade is called; no facade stub needed for this scenario.
