# Tasks: Phase 5 — Voice Transcription & RAG Observability

**Input**: Design documents from `specs/005-phase5-voice-metrics/`  
**Prerequisites**: plan.md ✅ · spec.md ✅ · research.md ✅ · data-model.md ✅ · contracts/ ✅ · quickstart.md ✅

**Scope**: Test-only phase — 4 new `*Spec.java` files + 1 minimal production patch (log masking).  
**Constitution**: TDD flow — write spec → confirm production code passes → fix any regressions.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared state)
- **[Story]**: User story label [US1], [US2], [US3]

---

## Phase 1: Setup

**Purpose**: Verify environment and confirm prior phases are still green before adding Phase 5 specs.

- [x] T001 Run existing Phase 1–4 test suite and confirm 0 failures: `./mvnw test` from `nex-rag/`
- [x] T002 Confirm test package directories exist: `nex-rag/src/test/java/com/exemple/nexrag/service/rag/voice/` and `nex-rag/src/test/java/com/exemple/nexrag/service/rag/metrics/embedding/` — create missing directories

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Apply the log-masking production patch required by FR-006d before writing voice tests. Tests that verify correct transcription output indirectly rely on the service behaving as specified.

**⚠️ CRITICAL**: T003 must be complete before US1 spec tasks begin (WhisperService behaviour changes).

- [x] T003 Patch `nex-rag/src/main/java/com/exemple/nexrag/service/rag/voice/WhisperService.java` — in `callWhisperApi()`, replace `log.debug("📝 [Whisper] Transcription brute : '{}'", transcript)` with `log.debug("📝 [Whisper] Transcription brute : [{} chars]", transcript != null ? transcript.length() : 0)` — in `validateTranscript()`, replace the `log.info("✅ [Whisper] Transcription réussie — {} caractères : '{}'", result.length(), result.length() > 100 ? result.substring(0, 100) + "..." : result)` with `log.info("✅ [Whisper] Transcription réussie — {} caractères", result.length())` (FR-006d / R-006)
- [x] T004 Build backend with patch applied and confirm 0 compile errors: `./mvnw clean package -DskipTests` from `nex-rag/`

**Checkpoint**: Patch applied and compiling — US1 spec work can begin.

---

## Phase 3: User Story 1 — Voice Input Transcription Tests (Priority: P1) 🎯 MVP

**Goal**: Full unit test coverage of `WhisperService` validation paths and `AudioTempFile` lifecycle. Every AC from US-1 maps to at least one `@Test` method.

**Independent Test**: `./mvnw test -Dtest="WhisperServiceSpec,AudioTempFileSpec"` passes with 0 failures.

### AudioTempFile Spec — create and lifecycle tests

- [x] T005 [P] [US1] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/voice/AudioTempFileSpec.java` — class skeleton with `@ExtendWith(MockitoExtension.class)`, `@DisplayName("Spec : AudioTempFile — Cycle de vie du fichier audio temporaire")`, `@Mock WhisperProperties props`, `@InjectMocks AudioTempFile audioTempFile`, `@BeforeEach` that stubs `when(props.getDefaultExtension()).thenReturn(".webm")`
- [x] T006 [P] [US1] In `AudioTempFileSpec.java` — add 4 test methods for `create()` (AC-13.4):
  - `devraitCreerFichierTempAvecExtensionDuNomOriginal` — call `audioTempFile.create("audio content".getBytes(), "audio.mp3")`, assert returned file name ends with `.mp3` and file exists on disk; delete in `@AfterEach`
  - `devraitUtiliserExtensionParDefautSiExtensionAbsente` — filename `"audio"` (no dot) → file ends with `.webm`
  - `devraitUtiliserExtensionParDefautSiNomNull` — filename `null` → file ends with `.webm`
  - `devraitEcrireLesBytesCorrectementDansFichierTemp` — `Files.readAllBytes(file.toPath())` equals original byte array
- [x] T007 [US1] In `AudioTempFileSpec.java` — add 2 test methods for `deleteSilently()` (AC-13.5):
  - `devraitSupprimerFichierSilencieusement` — create a real temp file, call `deleteSilently(file)`, assert `!file.exists()`
  - `devraitNePasLeverExceptionSiNullPasseADeleteSilently` — `assertThatCode(() -> audioTempFile.deleteSilently(null)).doesNotThrowAnyException()`

### WhisperService Spec — validation and availability tests

- [x] T008 [P] [US1] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/voice/WhisperServiceSpec.java` — class skeleton with `@ExtendWith(MockitoExtension.class)`, `@DisplayName("Spec : WhisperService — Validation et disponibilité du service Whisper")`, `@Mock WhisperProperties props`, `@Mock AudioTempFile audioTempFile`, `@InjectMocks WhisperService service`, `@BeforeEach` that calls `ReflectionTestUtils.setField(service, "apiKey", "test-key-fake")` and `when(props.getMinAudioBytes()).thenReturn(1_000)`
- [x] T009 [P] [US1] In `WhisperServiceSpec.java` — add 3 test methods for input validation (AC-13.2 / FR-001):
  - `devraitLeverIllegalArgumentExceptionPourAudioNull` — `transcribeAudio(null, "test.wav", "fr")` → `assertThatThrownBy(...).isInstanceOf(IllegalArgumentException.class).hasMessage("Données audio vides ou absentes")`
  - `devraitLeverIllegalArgumentExceptionPourAudioVide` — `new byte[0]` → same assertion
  - `devraitLeverIllegalArgumentExceptionSiTailleDepasse25Mo` — `new byte[26_214_401]` → `assertThatThrownBy(...).isInstanceOf(IllegalArgumentException.class)`
- [x] T010 [US1] In `WhisperServiceSpec.java` — add 3 test methods for availability and API guard (AC-13.3 / FR-001):
  - `devraitRetournerFalseSiOpenAiServiceNonInitialise` — with `@InjectMocks` only (no `@PostConstruct`), `isAvailable()` returns `false` because `openAiService == null`
  - `devraitNePasAppelerAudioTempFilePourAudioInvalide` — pass `new byte[0]`, assert `verifyNoInteractions(audioTempFile)`
  - `devraitNePasAppelerAudioTempFilePourTailleDepassee` — pass `new byte[26_214_401]`, assert `verifyNoInteractions(audioTempFile)`

### Verify and commit US1

- [x] T011 [US1] Run `./mvnw test -Dtest="WhisperServiceSpec,AudioTempFileSpec"` from `nex-rag/` — confirm all 12 tests pass and 0 failures
- [x] T012 [US1] Commit: `test(phase-5): add WhisperServiceSpec et AudioTempFileSpec — validation audio et cycle de vie fichier temporaire`

**Checkpoint**: US1 complete — Voice input validation tests fully functional and independently verifiable.

---

## Phase 4: User Story 2 — RAG Pipeline Observability Tests (Priority: P2)

**Goal**: Full unit test coverage of `RAGMetrics` — counters, timers, gauges, and the active-operations lifecycle. Uses `SimpleMeterRegistry` with no Spring context.

**Independent Test**: `./mvnw test -Dtest=RAGMetricsSpec` passes with 0 failures.

- [x] T013 [US2] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/metrics/RAGMetricsSpec.java` — class skeleton with `@DisplayName("Spec : RAGMetrics — Métriques centralisées du pipeline RAG")` (no `@ExtendWith` — no mocks needed), `MeterRegistry registry` and `RAGMetrics metrics` fields, `@BeforeEach` that sets `registry = new SimpleMeterRegistry()` and `metrics = new RAGMetrics(registry)`
- [x] T014 [P] [US2] In `RAGMetricsSpec.java` — add 4 test methods for ingestion counters (AC-14.1 / AC-14.2 / FR-007 / FR-008):
  - `devraitIncrementerCompteurSuccesApresIngestionReussie` — `recordIngestionSuccess("pdf", 100, 5)` → `registry.counter("rag_ingestion_files_total", "strategy", "pdf", "status", "success").count() == 1.0`
  - `devraitIncrementerTotalFichiersTraites` — same call → `metrics.getTotalFilesProcessed() == 1L`
  - `devraitEnregistrerErreurIngestionSansToucherSucces` — `recordIngestionError("pdf", "IO")` → error counter = 1.0; success counter for that strategy remains absent
  - `devraitNePasRecreeLeMeterSiDejaEnregistre` — two successive `recordIngestionSuccess` calls → counter = 2.0 (same meter reused from cache)
- [x] T015 [P] [US2] In `RAGMetricsSpec.java` — add 2 test methods for active-operation gauges (AC-14.2 / FR-009):
  - `devraitSuivreIngestionsActivesViaStartEtEnd` — `startIngestion()` → `getActiveIngestions() == 1`; `endIngestion()` → `getActiveIngestions() == 0`
  - `devraitSuivreQueriesActivesViaStartEtEnd` — `startQuery()` → `getActiveQueries() == 1`; `endQuery()` → `getActiveQueries() == 0` and `getTotalQueriesProcessed() == 1`
- [x] T016 [P] [US2] In `RAGMetricsSpec.java` — add 2 test methods for cache counters (AC-14.3 / FR-010):
  - `devraitIncrementerSeulementLeCompteurHitSansAffecterMiss` — `recordCacheHit("embedding")` → `rag_cache_hits_total{cache=embedding}` = 1.0; `registry.find("rag_cache_misses_total").counter()` is `null`
  - `devraitIncrementerSeulementLeCompteurMissSansAffecterHit` — inverse: `recordCacheMiss` only
- [x] T017 [US2] In `RAGMetricsSpec.java` — add 2 test methods for generation and token accumulation (AC-14.5 / FR-013):
  - `devraitAccumulerTokensGeneresCorrectementSurPlusieursAppels` — `recordGeneration(100, 80)` then `recordGeneration(200, 70)` → `getTotalTokensGenerated() == 150L`
  - `devraitEnregistrerCompteurTokensSurLeRegistre` — after `recordGeneration(100, 50)`, `registry.counter("rag_tokens_generated_total").count() == 50.0`
- [x] T018 [US2] Run `./mvnw test -Dtest=RAGMetricsSpec` from `nex-rag/` — confirm all 10 tests pass and 0 failures
- [x] T019 [US2] Commit: `test(phase-5): add RAGMetricsSpec — compteurs ingestion, jauges actives et accumulation tokens`

**Checkpoint**: US2 complete — all pipeline observability counters and gauges verified independently.

---

## Phase 5: User Story 3 — Embedding Latency Tracking Tests (Priority: P3)

**Goal**: Full unit test coverage of `OpenAiEmbeddingService` — verify that every embedding call records latency on success and an error counter on failure, and that no success metric is recorded when the model throws.

**Independent Test**: `./mvnw test -Dtest=OpenAiEmbeddingServiceSpec` passes with 0 failures.

- [x] T020 [US3] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/metrics/embedding/OpenAiEmbeddingServiceSpec.java` — class skeleton with `@ExtendWith(MockitoExtension.class)`, `@DisplayName("Spec : OpenAiEmbeddingService — Latence et erreurs des appels embedding")`, `@Mock EmbeddingModel embeddingModel`, `@Mock RAGMetrics ragMetrics`, `@InjectMocks OpenAiEmbeddingService service`
- [x] T021 [P] [US3] In `OpenAiEmbeddingServiceSpec.java` — add 3 test methods for `embedText()` (AC-14.4):
  - `devraitEnregistrerAppelAPIApresEmbeddingTexteReussi` — stub `embeddingModel.embed(anyString())` to return a mock `Response<Embedding>`; call `service.embedText("Bonjour")` → `verify(ragMetrics).recordApiCall(eq("embed_text"), anyLong())`; `verify(ragMetrics, never()).recordApiError(any())`
  - `devraitEnregistrerErreurAPIApresEchecEmbeddingTexte` — stub `embeddingModel.embed(anyString())` to throw `RuntimeException("API down")`; `assertThatThrownBy(() -> service.embedText("x")).isInstanceOf(RuntimeException.class)`; `verify(ragMetrics).recordApiError("embed_text")`
  - `devraitNePasEnregistrerSuccesEnCasDechecEmbeddingTexte` — same failure stub; verify `ragMetrics.recordApiCall(...)` is never called
- [x] T022 [P] [US3] In `OpenAiEmbeddingServiceSpec.java` — add 2 test methods for `embedSegment()` and `embedBatch()` (AC-14.4):
  - `devraitEnregistrerAppelAPIApresEmbeddingSegment` — stub `embeddingModel.embed(any(TextSegment.class))` → call `service.embedSegment(TextSegment.from("test"))` → `verify(ragMetrics).recordApiCall(eq("embed_segment"), anyLong())`
  - `devraitEnregistrerAppelAPIApresEmbeddingBatch` — stub `embeddingModel.embedAll(anyList())` → call `service.embedBatch(List.of("a", "b"))` → `verify(ragMetrics).recordApiCall(eq("embed_batch"), anyLong())`
- [x] T023 [US3] Run `./mvnw test -Dtest=OpenAiEmbeddingServiceSpec` from `nex-rag/` — confirm all 5 tests pass and 0 failures
- [x] T024 [US3] Commit: `test(phase-5): add OpenAiEmbeddingServiceSpec — latence et erreurs embedding par opération`

**Checkpoint**: US3 complete — all embedding latency tracking verified independently.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Full suite validation, coverage gate verification, and final Phase 5 commit.

- [x] T025 Run full Phase 5 suite together: `./mvnw test -Dtest="WhisperServiceSpec,AudioTempFileSpec,RAGMetricsSpec,OpenAiEmbeddingServiceSpec"` from `nex-rag/` — confirm 0 failures across all 28 test methods (28 > 23 estimated)
- [x] T026 Run full backend test suite: `./mvnw test` from `nex-rag/` — confirm Phase 1–4 tests still pass (555 tests, 0 failures, 3 pre-existing skipped)
- [x] T027 [P] Generate JaCoCo coverage report — voice/: 42% (transcription happy-path deferred to Phase 9 WireMock); metrics/: 40% (only Phase 5 methods covered — retrieval/reranking/LLM cost methods covered by other phases); metrics.embedding/: 96% ✅
- [x] T028 [P] Update `nexrag-test-plan-speckit.md` PHASE 5 section — confirm all spec class names, AC references, and example test signatures match the implemented spec files
- [x] T029 Final commit if any polish changes were made: `test(phase-5): coverage gate et nettoyage Phase 5`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS US1 (WhisperService patch must compile before testing)
- **US1 (Phase 3)**: Depends on Foundational (Phase 2) — T005–T007 and T008–T010 can run in parallel once T004 is complete
- **US2 (Phase 4)**: Depends on Foundational (Phase 2) only — fully independent of US1; can run in parallel with US1
- **US3 (Phase 5)**: Depends on Foundational (Phase 2) only — fully independent of US1 and US2; can run in parallel
- **Polish (Phase 6)**: Depends on all user story phases being complete

### User Story Dependencies

- **US1 (P1)**: Requires T003 (log mask patch) — no dependency on US2 or US3
- **US2 (P2)**: No production dependency — `RAGMetrics` exists and is injectable with `SimpleMeterRegistry`; independent of US1
- **US3 (P3)**: No production dependency — `OpenAiEmbeddingService` constructor-injectable; independent of US1/US2

### Within Each User Story

- Spec skeleton task → test method tasks → run task → commit task (sequential within a story)
- `AudioTempFileSpec` (T005–T007) and `WhisperServiceSpec` (T008–T010) tasks marked [P] are parallelisable

### Parallel Opportunities

- T005, T008 (spec skeletons for AudioTempFile and WhisperService) can start simultaneously
- T006, T007 (AudioTempFile methods) and T009, T010 (WhisperService methods) can start simultaneously within their class
- T014, T015, T016 (different RAGMetrics test groups) can start simultaneously
- T021, T022 (OpenAiEmbeddingService test groups) can start simultaneously
- T027, T028 (coverage report and test plan update) can start simultaneously

---

## Parallel Example: User Story 1

```bash
# After T004 is complete, launch spec skeletons in parallel:
Task T005: "Create AudioTempFileSpec.java skeleton"
Task T008: "Create WhisperServiceSpec.java skeleton"

# After skeletons exist, add test methods in parallel:
Task T006: "Add AudioTempFile create() tests"    → T007: "Add deleteSilently() tests"
Task T009: "Add WhisperService validation tests" → T010: "Add isAvailable() tests"
```

## Parallel Example: User Stories 2 & 3

```bash
# US2 and US3 have no dependency on each other — start both after Phase 2:
Task T013: "Create RAGMetricsSpec.java"              (US2)
Task T020: "Create OpenAiEmbeddingServiceSpec.java"  (US3)

# Within US2, test groups are parallelisable:
Task T014: "Ingestion counter tests"
Task T015: "Active-operations gauge tests"
Task T016: "Cache hit/miss counter tests"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T002)
2. Complete Phase 2: Foundational patch (T003–T004)
3. Complete Phase 3: US1 voice tests (T005–T012)
4. **STOP and VALIDATE**: `./mvnw test -Dtest="WhisperServiceSpec,AudioTempFileSpec"` — 12 tests green
5. Deliver: audio validation and temp-file lifecycle are fully spec-covered

### Incremental Delivery

1. Setup + Foundational → patch compiled (T001–T004)
2. US1 voice tests → 12 tests green → commit (T005–T012)
3. US2 metrics tests → 10 tests green → commit (T013–T019)
4. US3 embedding tests → 5 tests green → commit (T020–T024)
5. Polish → 23 total tests, coverage gate ≥ 80% (T025–T029)

### Parallel Team Strategy

With two developers after Phase 2 completion:

- **Developer A**: US1 (WhisperServiceSpec + AudioTempFileSpec) — T005–T012
- **Developer B**: US2 + US3 (RAGMetricsSpec + OpenAiEmbeddingServiceSpec) — T013–T024
- Merge and run T025–T029 together

---

## Notes

- [P] tasks operate on different files — no merge conflicts
- All `@DisplayName` values MUST be in French, imperative: `"DOIT [action] quand [condition]"`
- `ReflectionTestUtils.setField(service, "apiKey", "test-key-fake")` in `@BeforeEach` (not `@Mock`) — see R-001
- `new SimpleMeterRegistry()` requires `import io.micrometer.core.instrument.simple.SimpleMeterRegistry`
- Verify tests FAIL before implementation only applies when writing new production code; since production classes exist, tests should PASS — fix any regressions found
- Commit after each user story phase (T012, T019, T024) — not after each individual task
- Phase 9 (end-to-end) is where WireMock-based happy-path transcription test goes — out of scope here

---

## Task Count Summary

| Phase | Tasks | US | Tests Added |
|---|---|---|---|
| Setup | T001–T002 | — | 0 |
| Foundational | T003–T004 | — | 0 (1 prod patch) |
| US1 Voice | T005–T012 | US1 | 12 |
| US2 Metrics | T013–T019 | US2 | 10 |
| US3 Embedding | T020–T024 | US3 | 5 |
| Polish | T025–T029 | — | 0 |
| **Total** | **29 tasks** | **3 stories** | **27 tests** |
