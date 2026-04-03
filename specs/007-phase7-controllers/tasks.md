# Tasks: Phase 7 — Controllers Unit Tests (MockMvc)

**Input**: Design documents from `specs/007-phase7-controllers/`  
**Prerequisites**: plan.md ✅ · spec.md ✅ · research.md ✅ · data-model.md ✅ · quickstart.md ✅

**Organization**: Tasks are grouped by user story to enable independent implementation and
testing. US3 and US4 (both P3) can be executed in parallel. US5 and US6 (both P4) can be
executed in parallel. Each story produces a fully green, independently runnable `*Spec.java`
before the next priority begins.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies between them)
- **[Story]**: Maps task to its user story from spec.md

---

## Phase 1: Setup

**Purpose**: Verify test directory structure and package layout before any spec class is created.

- [x] T001 Verify `nex-rag/src/test/java/com/exemple/nexrag/service/rag/controller/` exists; create it if absent (5 spec classes land here)
- [x] T002 Verify `nex-rag/src/test/java/com/exemple/nexrag/controller/` exists; create it if absent (`MultimodalCrudControllerSpec` mirrors the production package `com.exemple.nexrag.controller`)

**Checkpoint**: Both test package directories exist — spec class creation can begin.

---

## Phase 2: Foundational (Blocking Prerequisite)

**Purpose**: Apply the one required production code change before US2 (`StreamingAssistantControllerSpec`) can test the empty-query edge case. This phase is a hard prerequisite for Task T010.

**⚠️ CRITICAL**: T010 (US2) depends on T004 being complete and compiling.

- [x] T003 Add `@NotBlank` annotation to the `query` field in `nex-rag/src/main/java/com/exemple/nexrag/service/rag/streaming/model/StreamingRequest.java` — add import `jakarta.validation.constraints.NotBlank`
- [x] T004 Add `@Valid` to the `@RequestBody StreamingRequest request` parameter of `streamPost()` in `nex-rag/src/main/java/com/exemple/nexrag/service/rag/controller/StreamingAssistantController.java` — add import `jakarta.validation.Valid`; run `./mvnw compile -q` to confirm no regression

**Checkpoint**: `./mvnw compile -q` passes — US2 empty-query validation is now enforced at the binding layer.

---

## Phase 3: User Story 1 — Multimodal Ingestion Controller (Priority: P1) 🎯 MVP

**Goal**: Verify that `MultimodalIngestionController` correctly routes all 11 endpoints and that `IngestionExceptionHandler` maps custom exceptions to the right HTTP status codes.

**Independent Test**: `./mvnw test -Dtest=MultimodalIngestionControllerSpec` passes with zero failures; controller class reaches ≥ 80 % branch coverage.

- [x] T005 [US1] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/controller/MultimodalIngestionControllerSpec.java` — class skeleton: `@DisplayName("Spec : MultimodalIngestionController — API d'ingestion REST")`, `@WebMvcTest(MultimodalIngestionController.class)`, `@Import(IngestionExceptionHandler.class)`, `@MockBean IngestionFacade ingestionFacade`, `@Autowired MockMvc mockMvc`
- [x] T006 [US1] Implement upload scenarios in `MultimodalIngestionControllerSpec.java` — 4 tests: `shouldReturn200ForSyncUpload`, `shouldReturn202ForAsyncUpload`, `shouldReturn409ForAsyncDuplicate`, `shouldReturn202ForBatchUpload` — stub `ingestionFacade.uploadSync`, `uploadAsync`, `uploadBatch` with the mock contracts from `data-model.md` §T1
- [x] T007 [US1] Implement batch-detailed and monitoring scenarios in `MultimodalIngestionControllerSpec.java` — 5 tests: `shouldReturn202ForBatchDetailedUpload`, `shouldReturn200ForStatus`, `shouldReturn200ForRollback`, `shouldReturn200ForActiveIngestions`, `shouldReturn200ForStats`, `shouldReturn200ForStrategies` — stub `uploadBatchDetailed`, `getStatus`, `rollback`, `getActiveIngestions`, `getStats`, `getStrategies`
- [x] T008 [US1] Implement health endpoint scenarios in `MultimodalIngestionControllerSpec.java` — 3 tests: `shouldReturn200ForDetailedHealthWhenHealthy`, `shouldReturn503ForDetailedHealthWhenUnhealthy`, `shouldReturn200ForBasicHealth` — assert static map fields (`status`, `service`, `streaming`, `duplicateDetection`, `websocketProgress`) for basic health; stub `getDetailedHealth` returning healthy/unhealthy `DetailedHealthResponse`
- [x] T009 [US1] Implement `@ControllerAdvice` exception and missing-param scenarios in `MultimodalIngestionControllerSpec.java` — 3 tests: `shouldReturn409WhenDuplicateFileExceptionThrown`, `shouldReturn404WhenResourceNotFoundExceptionThrown`, `shouldReturn400WhenFileMissing` — use `thenThrow(new DuplicateFileException(...))` and `thenThrow(new ResourceNotFoundException(...))` for the first two; submit multipart with no file part for the third

**Checkpoint**: `./mvnw test -Dtest=MultimodalIngestionControllerSpec` — 16 tests green. US1 is independently done.

---

## Phase 4: User Story 2 — Streaming Assistant Controller (Priority: P2)

**Goal**: Verify that `StreamingAssistantController` returns `text/event-stream` for both GET and POST, cancels streams, returns health, and rejects empty queries via bean validation.

**Independent Test**: `./mvnw test -Dtest=StreamingAssistantControllerSpec` passes with zero failures.

- [x] T010 [US2] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/controller/StreamingAssistantControllerSpec.java` — class skeleton: `@WebMvcTest(StreamingAssistantController.class)`, `@MockBean StreamingFacade streamingFacade`, `@Autowired MockMvc mockMvc`; no `@Import` (no custom advice bean for this controller)
- [x] T011 [US2] Implement SSE and cancellation scenarios in `StreamingAssistantControllerSpec.java` — 4 tests: `shouldReturnTextEventStreamForGetRequest`, `shouldReturnTextEventStreamForPostRequest`, `shouldReturn200ForCancelStream`, `shouldReturn200ForHealth` — stub `streamingFacade.startStream(any())` returning `new SseEmitter()`; stub `streamingFacade.cancelStream(any())` as void; assert `Content-Type` contains `text/event-stream` for stream endpoints
- [x] T012 [US2] Implement empty-query validation scenario in `StreamingAssistantControllerSpec.java` — 1 test: `shouldReturn400WhenQueryIsEmpty` — POST to `/api/v1/assistant/stream` with body `{"query": ""}` or `{"query": null}`; assert HTTP 400 and verify `streamingFacade.startStream` is never invoked (`verify(streamingFacade, never()).startStream(any())`)

**Checkpoint**: `./mvnw test -Dtest=StreamingAssistantControllerSpec` — 5 tests green. US2 done.

---

## Phase 5: User Story 3 — CRUD Controller for Embedding Management (Priority: P3)

**Goal**: Verify that `MultimodalCrudController` routes all 8 delete/read endpoints and that `CrudExceptionHandler` maps `ResourceNotFoundException` to HTTP 404.

**Independent Test**: `./mvnw test -Dtest=MultimodalCrudControllerSpec` passes with zero failures; test placed in `com.exemple.nexrag.controller` package.

- [x] T013 [US3] Create `nex-rag/src/test/java/com/exemple/nexrag/controller/MultimodalCrudControllerSpec.java` — class skeleton: `@DisplayName("Spec : MultimodalCrudController — API CRUD embeddings")`, `@WebMvcTest(MultimodalCrudController.class)`, `@Import(CrudExceptionHandler.class)`, `@MockBean CrudFacade crudFacade`, `@Autowired MockMvc mockMvc`
- [x] T014 [US3] Implement deletion scenarios in `MultimodalCrudControllerSpec.java` — 5 tests: `shouldReturn200ForDeleteById`, `shouldReturn200ForDeleteBatchById`, `shouldReturn200ForDeleteTextBatch`, `shouldReturn200ForDeleteImageBatch`, `shouldReturn200ForDeleteAll` — stub `crudFacade.deleteById`, `deleteBatchById`, `deleteBatch(list, TEXT)`, `deleteBatch(list, IMAGE)`, `deleteAll("DELETE_ALL_FILES")` returning `DeleteResponse`
- [x] T015 [US3] Implement read scenarios in `MultimodalCrudControllerSpec.java` — 3 tests: `shouldReturn200ForCheckDuplicate`, `shouldReturn200ForGetBatchInfo`, `shouldReturn200ForGetSystemStats` — stub `crudFacade.checkDuplicate`, `getBatchInfo`, `getSystemStats`; assert HTTP 200 and non-null response body
- [x] T016 [US3] Implement exception and missing-param scenarios in `MultimodalCrudControllerSpec.java` — 2 tests: `shouldReturn404WhenResourceNotFoundExceptionThrown`, `shouldReturn400WhenConfirmationParamMissing` — stub `crudFacade.deleteById` to throw `ResourceNotFoundException`; submit `DELETE /api/v1/crud/files/all` without `confirmation` param for the second test

**Checkpoint**: `./mvnw test -Dtest=MultimodalCrudControllerSpec` — 10 tests green. US3 done.

---

## Phase 6: User Story 4 — Voice Transcription Controller (Priority: P3)

**Goal**: Verify that `VoiceController` routes transcription and health requests, forwards language parameters correctly, and that `VoiceExceptionHandler` handles exceptions.

**Independent Test**: `./mvnw test -Dtest=VoiceControllerSpec` passes with zero failures.

*Note: US4 is also P3 — can be executed in parallel with Phase 5 (US3) by a second developer.*

- [x] T017 [P] [US4] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/controller/VoiceControllerSpec.java` — class skeleton: `@DisplayName("Spec : VoiceController — API transcription audio")`, `@WebMvcTest(VoiceController.class)`, `@Import(VoiceExceptionHandler.class)`, `@MockBean VoiceFacade voiceFacade`, `@Autowired MockMvc mockMvc`
- [x] T018 [P] [US4] Implement transcription scenarios in `VoiceControllerSpec.java` — 3 tests: `shouldReturn200ForSuccessfulTranscription`, `shouldForwardLanguageCodeToFacade`, `shouldUseDefaultLanguageWhenNotProvided` — stub `voiceFacade.transcribe(any(), eq("en"))` for the language test and `voiceFacade.transcribe(any(), eq(VoiceConstants.DEFAULT_LANGUAGE))` for the default test; use `ArgumentCaptor` on the language parameter to verify forwarding
- [x] T019 [P] [US4] Implement health and exception scenarios in `VoiceControllerSpec.java` — 3 tests: `shouldReturn200ForHealthEndpoint`, `shouldReturn400WhenIllegalArgumentExceptionThrown`, `shouldReturn400WhenAudioFileMissing` — stub `voiceFacade.health()` returning `VoiceHealthResponse`; stub `voiceFacade.transcribe` to throw `IllegalArgumentException` for the second; submit request without `audio` part for the third

**Checkpoint**: `./mvnw test -Dtest=VoiceControllerSpec` — 6 tests green. US4 done.

---

## Phase 7: User Story 5 — Metrics and Actuator Controller (Priority: P4)

**Goal**: Verify that `MetricsController` returns Prometheus plain-text, health JSON, and metrics summary, using a `@TestConfiguration`-provided registry.

**Independent Test**: `./mvnw test -Dtest=MetricsControllerSpec` passes with zero failures.

- [x] T020 [US5] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/controller/MetricsControllerSpec.java` — class skeleton: `@WebMvcTest(MetricsController.class)`, inner `@TestConfiguration` static class `TestMetricsConfig` with a `@Bean PrometheusMeterRegistry` that registers the four required meters (`rag.query.duration` timer, `rag.queries.total` counter, `rag.queries.success` counter, `rag.connections.active` gauge); annotate test class with `@Import(MetricsControllerSpec.TestMetricsConfig.class)`
- [x] T021 [US5] Implement Prometheus and health scenarios in `MetricsControllerSpec.java` — 2 tests: `shouldReturnPlainTextForPrometheusEndpoint`, `shouldReturn200ForHealthEndpoint` — assert `Content-Type` is `text/plain` and body is non-empty for prometheus; assert JSON body contains `status` and `application` fields for health
- [x] T022 [US5] Implement metrics summary scenario in `MetricsControllerSpec.java` — 1 test: `shouldReturn200ForMetricsSummaryWithNonNullValues` — GET `/api/actuator/metrics/summary`; assert JSON body contains keys `queries`, `performance`, and `connections`; assert `$.queries.total` and `$.connections.active` are numeric (≥ 0)

**Checkpoint**: `./mvnw test -Dtest=MetricsControllerSpec` — 3 tests green. US5 done.

---

## Phase 8: User Story 6 — WebSocket Session Statistics Controller (Priority: P4)

**Goal**: Verify that `WebSocketStatsController` routes all 6 session-management endpoints including the HTTP 404 path for unknown session IDs and the cleanup report fields.

**Independent Test**: `./mvnw test -Dtest=WebSocketStatsControllerSpec` passes with zero failures.

*Note: US6 is also P4 — can be executed in parallel with Phase 7 (US5) by a second developer.*

- [x] T023 [P] [US6] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/controller/WebSocketStatsControllerSpec.java` — class skeleton: `@DisplayName("Spec : WebSocketStatsController — statistiques sessions WebSocket")`, `@WebMvcTest(WebSocketStatsController.class)`, `@MockBean WebSocketSessionManager sessionManager`, `@Autowired MockMvc mockMvc`; no `@Import` (no custom advice bean)
- [x] T024 [P] [US6] Implement stats and session list scenarios in `WebSocketStatsControllerSpec.java` — 3 tests: `shouldReturn200ForStats`, `shouldReturn200ForActiveSessionCount`, `shouldReturn200ForSessionList` — stub `sessionManager.getStats()` returning `SessionStats(active=3, total=10)`; stub `sessionManager.getActiveSessionCount()` returning `5`; stub `sessionManager.getActiveSessionIds()` returning `Set.of("s1","s2")`; assert `$.activeSessions` for active count test
- [x] T025 [P] [US6] Implement per-session info, cleanup, and health scenarios in `WebSocketStatsControllerSpec.java` — 4 tests: `shouldReturn200ForKnownSessionInfo`, `shouldReturn404ForUnknownSessionInfo`, `shouldReturn200ForCleanupWithCorrectFields`, `shouldReturn200ForHealth` — stub `sessionManager.getSessionInfo("s1")` returning a non-null `SessionInfo`; stub `getSessionInfo("unknown")` returning `null`; stub before/after counts for cleanup (5 → 3); assert `$.cleaned` equals `2` and `$.remaining` equals `3`; assert `$.status` equals `"UP"` for health

**Checkpoint**: `./mvnw test -Dtest=WebSocketStatsControllerSpec` — 7 tests green. US6 done.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Run the full Phase 7 suite, verify coverage, and enforce constitution conventions across all six spec classes.

- [x] T026 Run the complete Phase 7 test suite in `nex-rag/` — `./mvnw test -Dtest="MultimodalIngestionControllerSpec,StreamingAssistantControllerSpec,MultimodalCrudControllerSpec,MetricsControllerSpec,VoiceControllerSpec,WebSocketStatsControllerSpec"` — confirm 47 tests pass with zero failures and zero errors
- [x] T027 Generate JaCoCo coverage report — `./mvnw test jacoco:report` — open `nex-rag/target/site/jacoco/index.html` and verify all six controller classes show ≥ 80 % line and branch coverage; log any class below threshold and add missing test methods
- [x] T028 Audit all `@DisplayName` annotations across the six spec classes — confirm every class-level and method-level name follows the French convention `"DOIT [action] quand [condition]"` (Constitution Principle III); fix any English or non-imperative display names

**Checkpoint**: All 47 tests pass, all 6 classes at ≥ 80 % coverage, all display names in French. Phase 7 complete — ready for Phase 8 (Interceptor & Validation).

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup)
    └── Phase 2 (Foundational — @NotBlank change)
            ├── Phase 3 (US1 — Ingestion) ← P1, start first
            ├── Phase 4 (US2 — Streaming) ← P2, REQUIRES Phase 2 for T010
            ├── Phase 5 (US3 — CRUD)      ← P3, independent of Phase 2
            ├── Phase 6 (US4 — Voice)     ← P3, independent of Phase 2, parallel with Phase 5
            ├── Phase 7 (US5 — Metrics)   ← P4, independent
            └── Phase 8 (US6 — WebSocket) ← P4, independent, parallel with Phase 7
                    └── Phase 9 (Polish)
```

### Task-Level Dependencies

- **T003–T004** (production change): Must complete before **T010** (empty-query test in US2)
- **T005** (skeleton): Must complete before **T006–T009**
- **T010** (skeleton): Must complete before **T011–T012**
- **T013** (skeleton): Must complete before **T014–T016**
- **T017** (skeleton): Must complete before **T018–T019**
- **T020** (skeleton + @TestConfiguration): Must complete before **T021–T022**
- **T023** (skeleton): Must complete before **T024–T025**
- **T026–T028** (polish): Require all spec classes complete

### Parallel Opportunities

- **T001 ∥ T002** — both setup tasks touch different directories
- **T017–T019 (US4) ∥ T013–T016 (US3)** — different controllers, different packages
- **T023–T025 (US6) ∥ T020–T022 (US5)** — different controllers, no shared state
- **T026 ∥ T027** — test execution and coverage report can overlap
- After Phase 2: US3, US4, US5, US6 can all start without waiting for US1 or US2

---

## Parallel Execution Examples

```bash
# Phase 5 + Phase 6 in parallel (both P3 stories):
# Terminal 1:
./mvnw test -Dtest=MultimodalCrudControllerSpec

# Terminal 2 (simultaneously):
./mvnw test -Dtest=VoiceControllerSpec
```

```bash
# Phase 7 + Phase 8 in parallel (both P4 stories):
# Terminal 1:
./mvnw test -Dtest=MetricsControllerSpec

# Terminal 2 (simultaneously):
./mvnw test -Dtest=WebSocketStatsControllerSpec
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. Complete Phase 1: Setup directories
2. Complete Phase 2: Add `@NotBlank` to `StreamingRequest`
3. Complete Phase 3: `MultimodalIngestionControllerSpec` (16 tests)
4. **STOP and VALIDATE**: `./mvnw test -Dtest=MultimodalIngestionControllerSpec` — all green
5. The most critical controller is fully tested and documented

### Incremental Delivery (Priority Order)

1. Phase 1 + 2 → Foundation ready
2. Phase 3 → Ingestion controller tested (MVP)
3. Phase 4 → Streaming controller tested
4. Phase 5 + 6 (parallel) → CRUD + Voice controllers tested
5. Phase 7 + 8 (parallel) → Metrics + WebSocket controllers tested
6. Phase 9 → Polish, coverage gate enforced

---

## Notes

- `[P]` tasks operate on different files with no mutual dependency
- Every `*Spec.java` must use `@ExtendWith` provided by `@WebMvcTest` — do NOT add `@ExtendWith(MockitoExtension.class)` manually
- `@MockBean` resets mocks between tests automatically — no manual `reset()` needed
- `MultimodalCrudControllerSpec` lives in `com.exemple.nexrag.controller` — do not place it in `service.rag.controller`
- Commit after each phase using the convention: `test(phase-7): add <ClassName>Spec — <description>`
- Each phase checkpoint must pass before the next phase begins
