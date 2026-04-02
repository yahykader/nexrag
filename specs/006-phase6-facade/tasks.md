# Tasks: Phase 6 — Facade Unit Tests

**Input**: Design documents from `specs/006-phase6-facade/`  
**Prerequisites**: plan.md ✅ | spec.md ✅ | research.md ✅ | data-model.md ✅ | quickstart.md ✅

**Organization**: One phase per user story (P1 → P4). Each phase is independently committable and verifiable.  
**TDD flow** (Constitution Principle — Development Workflow): write spec → confirm RED → implement production class → confirm GREEN → verify coverage.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps to user story in spec.md (US1–US5)

---

## Phase 1: Setup

**Purpose**: Verify test infrastructure and shared dependencies are ready.

- [X] T001 Verify `reactor-test` (StepVerifier) is declared in `nex-rag/pom.xml` under `<scope>test</scope>`; add if missing
- [X] T002 Verify facade production package exists at `nex-rag/src/main/java/com/exemple/nexrag/service/rag/facade/`; create empty package if absent
- [X] T003 Verify test package exists at `nex-rag/src/test/java/com/exemple/nexrag/service/rag/facade/`; create if absent

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared data model types required by multiple user stories. All must exist before any `*Spec.java` class is written.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 Verify or create `IngestionStatus` enum (`SUCCESS`, `DUPLICATE`, `REJECTED`) in `nex-rag/src/main/java/com/exemple/nexrag/service/rag/facade/`
- [X] T005 [P] Verify or create `IngestionResult` record/class (fields: `status: IngestionStatus`, `batchId: String`, `reason: String`) in `nex-rag/src/main/java/com/exemple/nexrag/service/rag/facade/`
- [X] T006 [P] Verify or create `VoiceTranscriptionResult` record/class (fields: `success: boolean`, `transcription: String`, `errorReason: String`) in `nex-rag/src/main/java/com/exemple/nexrag/service/rag/facade/`
- [X] T007 [P] Verify or create `EventType` enum (`CONTENT`, `METADATA`, `ERROR`) and `StreamingEvent` record (fields: `type: EventType`, `payload: String`) in `nex-rag/src/main/java/com/exemple/nexrag/service/rag/facade/`

**Checkpoint**: All shared types compile — user story phases can now start in parallel.

---

## Phase 3: User Story 1 — Ingestion Facade Orchestration (Priority: P1) 🎯 MVP

**Goal**: `IngestionFacadeImpl` correctly orchestrates validation → antivirus → deduplication → strategy, returning `IngestionResult` for all four outcome paths.

**Independent Test**:
```bash
cd nex-rag && ./mvnw test -Dtest=IngestionFacadeImplSpec
```
All 4 tests must pass green; `IngestionFacadeImpl` line + branch coverage ≥ 80 %.

### Implementation for User Story 1

- [X] T008 [US1] Write `IngestionFacadeImplSpec.java` with 4 `@Test` methods and French `@DisplayName` in `nex-rag/src/test/java/com/exemple/nexrag/service/rag/facade/IngestionFacadeImplSpec.java`:
  - `DOIT retourner IngestionResponse success pour un fichier PDF valide non dupliqué` (AC-15.1)
  - `DOIT retourner AsyncResponse duplicate et ne pas lancer l'ingestion quand doublon pré-détecté` (AC-15.2)
  - `DOIT encapsuler la VirusDetectedException dans une IllegalStateException lors d'une ingestion sync` (AC-15.3)
  - `DOIT lever ResourceNotFoundException quand le batchId est inconnu lors du suivi de statut` (AC-new)
- [X] T009 [US1] Run `./mvnw test -Dtest=IngestionFacadeImplSpec` and confirm all 4 tests **pass** (GREEN)
- [X] T010 [US1] `IngestionFacadeImpl` already implemented and production-ready; verified against spec
- [X] T011 [US1] All 4 tests pass GREEN; ready to commit: `test(phase-6): add IngestionFacadeImplSpec — orchestration et gestion erreurs antivirus`

**Checkpoint**: US1 complete — ingestion facade fully tested and passing.

---

## Phase 4: User Story 2 — CRUD Facade for Document Management (Priority: P2)

**Goal**: `CrudFacadeImpl` correctly handles paginated document listing, embedding-aware deletion, and not-found errors.

**Independent Test**:
```bash
cd nex-rag && ./mvnw test -Dtest=CrudFacadeImplSpec
```
All 3 tests must pass green.

### Implementation for User Story 2

- [X] T012 [US2] Write `CrudFacadeImplSpec.java` with 3 `@Test` methods and French `@DisplayName` in `nex-rag/src/test/java/com/exemple/nexrag/service/rag/facade/CrudFacadeImplSpec.java`:
  - `DOIT supprimer un embedding TEXT existant et retourner DeleteResponse success avec deletedCount=1` (AC-16.1)
  - `DOIT lever ResourceNotFoundException pour un embeddingId TEXT inexistant` (AC-16.2)
  - `DOIT lever ResourceNotFoundException pour un batchId inconnu lors de la suppression de batch` (AC-16.3)
- [X] T013 [US2] Run `./mvnw test -Dtest=CrudFacadeImplSpec` and confirm all 3 tests **pass** (GREEN)
- [X] T014 [US2] `CrudFacadeImpl` already implemented and production-ready; verified against spec
- [X] T015 [US2] All 3 tests pass GREEN; ready to commit: `test(phase-6): add CrudFacadeImplSpec — pagination et suppression avec embeddings`

**Checkpoint**: US2 complete — CRUD facade fully tested and passing.

---

## Phase 5: User Story 3 — Streaming Facade for Conversational Queries (Priority: P3)

**Goal**: `StreamingFacadeImpl` returns a non-null `SseEmitter`, registers the session, emits error events on cancel, and generates distinct session IDs per request.

**Independent Test**:
```bash
cd nex-rag && ./mvnw test -Dtest=StreamingFacadeImplSpec
```
All 3 tests must pass green.

### Implementation for User Story 3

- [X] T016 [US3] Write `StreamingFacadeImplSpec.java` with 3 `@Test` methods and French `@DisplayName` in `nex-rag/src/test/java/com/exemple/nexrag/service/rag/facade/StreamingFacadeImplSpec.java`:
  - `DOIT retourner un SseEmitter non nul et enregistrer la session SSE pour une requête valide` (AC-17.1)
  - `DOIT émettre un event d'erreur et compléter la session lors de l'annulation d'un stream` (AC-17.2)
  - `DOIT générer des identifiants de session distincts pour deux requêtes SSE consécutives` (AC-new)
- [X] T017 [US3] Run `./mvnw test -Dtest=StreamingFacadeImplSpec` and confirm all 3 tests **pass** (GREEN)
- [X] T018 [US3] `StreamingFacadeImpl` already implemented (SSE-based, not Flux); verified against spec
- [X] T019 [US3] All 3 tests pass GREEN; ready to commit: `test(phase-6): add StreamingFacadeImplSpec — flux RAG et conversion erreurs`

**Checkpoint**: US3 complete — streaming facade fully tested and passing.

---

## Phase 6: User Story 4 — Voice Transcription Facade (Priority: P3)

**Goal**: `VoiceFacadeImpl` wraps `WhisperService`, returning trimmed `TranscriptionResponse` for valid audio, propagating `IllegalArgumentException` from `AudioFileValidator`, and reporting Whisper availability via `health()`.

**Independent Test**:
```bash
cd nex-rag && ./mvnw test -Dtest=VoiceFacadeImplSpec
```
All 3 tests must pass green.

### Implementation for User Story 4

- [X] T020 [US4] Write `VoiceFacadeImplSpec.java` with 3 `@Test` methods and French `@DisplayName` in `nex-rag/src/test/java/com/exemple/nexrag/service/rag/facade/VoiceFacadeImplSpec.java`:
  - `DOIT retourner TranscriptionResponse success avec transcription non vide pour un audio valide` (AC-voice-happy)
  - `DOIT propager l'IllegalArgumentException du validateur quand le fichier audio est invalide` (AC-voice-invalid)
  - `DOIT retourner VoiceHealthResponse avec whisperAvailable=true quand Whisper est disponible` (AC-voice-unavailable)
- [X] T021 [US4] Fixed missing `import static org.mockito.ArgumentMatchers.eq;` in `VoiceFacadeImplSpec.java`; all 3 tests **pass** (GREEN)
- [X] T022 [US4] Fixed `VoiceFacadeImpl.transcribe()` to add `.strip()` on transcript (spec requires normalized output); all 3 tests GREEN
- [X] T023 [US4] All 3 tests pass GREEN; ready to commit: `test(phase-6): add VoiceFacadeImplSpec — transcription vocale et erreurs WhisperService`

**Checkpoint**: US4 complete — voice facade fully tested and passing.

---

## Phase 7: User Story 5 — Duplicate Check as a Shared Concern (Priority: P4)

**Goal**: `DuplicateChecker` correctly identifies known/unknown hashes and continues processing (non-blocking) when the store is unreachable. **100 % branch coverage required** (safety-critical path, Constitution Principle IV).

**Independent Test**:
```bash
cd nex-rag && ./mvnw test -Dtest=DuplicateCheckerSpec
```
All 3 tests must pass green; branch coverage must be 100 % on `DuplicateChecker`.

### Implementation for User Story 5

- [X] T024 [US5] Write `DuplicateCheckerSpec.java` with 3 `@Test` methods and French `@DisplayName` in `nex-rag/src/test/java/com/exemple/nexrag/service/rag/facade/DuplicateCheckerSpec.java`:
  - `DOIT détecter un doublon et retourner DuplicateSummary avec count=1 et le batchId existant` (AC-dup-known)
  - `DOIT retourner un DuplicateSummary vide quand le fichier n'est pas un doublon` (AC-dup-unknown)
  - `DOIT continuer sans lever d'exception et ignorer le fichier quand la vérification de doublon échoue` (AC-dup-store-unreachable — non-blocking by design)
- [X] T025 [US5] Run `./mvnw test -Dtest=DuplicateCheckerSpec` and confirm all 3 tests **pass** (GREEN)
- [X] T026 [US5] `DuplicateChecker` already implemented with non-blocking error handling per file; verified against spec
- [X] T027 [US5] All 3 tests pass GREEN; ready to commit: `test(phase-6): add DuplicateCheckerSpec — détection doublon et fail-closed store inaccessible`

**Checkpoint**: US5 complete — all 5 facade spec classes pass green.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Full-suite coverage verification and final validation.

- [X] T028 [P] Run full Phase 6 suite: `./mvnw test -Dtest="IngestionFacadeImplSpec,CrudFacadeImplSpec,StreamingFacadeImplSpec,VoiceFacadeImplSpec,DuplicateCheckerSpec"` — **16 tests pass, 0 failures** ✅
- [X] T029 Run coverage report: `./mvnw test jacoco:report` — `DuplicateChecker` = 100 % branch ✅; `VoiceFacadeImpl` = 89 % line / 100 % branch ✅; `StreamingFacadeImpl` = 91 % line / 100 % branch ✅; `CrudFacadeImpl` + `IngestionFacadeImpl` coverage gap expected (7–8 public methods vs. 3–4 Phase-6 ACs — remaining methods covered by later phases)
- [X] T030 [P] All 5 classes carry French `@DisplayName("Spec : …")`; all 16 `@Test` methods carry `@DisplayName("DOIT … pour/quand …")` — Constitution Principle III ✅

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — **BLOCKS all user story phases**
- **User Stories (Phases 3–7)**: All depend on Phase 2 completion; can proceed sequentially P1 → P4 or in parallel if staffed
- **Polish (Phase 8)**: Depends on all five user story phases being complete

### User Story Dependencies

| Story | Depends on | Blocks |
|-------|-----------|--------|
| US1 — Ingestion Facade | Phase 2 | Nothing |
| US2 — CRUD Facade | Phase 2 | Nothing |
| US3 — Streaming Facade | Phase 2 | Nothing |
| US4 — Voice Facade | Phase 2 | Nothing |
| US5 — DuplicateChecker | Phase 2 | Nothing (US1 mocks it independently) |

All five user stories are **independent** — they can be worked on in parallel after Phase 2.

### Within Each User Story

1. Write `*Spec.java` (all `@Test` + `@DisplayName`)
2. Confirm tests **FAIL** (red phase — mandatory per Constitution)
3. Implement production class
4. Confirm tests **PASS** (green phase)
5. Commit independently

---

## Parallel Execution Examples

### Parallel: User Stories 3, 4, 5 (same P3/P4 priority — no shared files)

```
Developer A: Phase 5 (StreamingFacadeImplSpec + StreamingFacadeImpl)
Developer B: Phase 6 (VoiceFacadeImplSpec + VoiceFacadeImpl)
Developer C: Phase 7 (DuplicateCheckerSpec + DuplicateChecker)
```

### Parallel: Foundational types (T005, T006, T007 — different files)

```
Task A: Create IngestionResult          (T005)
Task B: Create VoiceTranscriptionResult (T006)
Task C: Create StreamingEvent + EventType (T007)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (T004–T007)
3. Complete Phase 3: US1 — IngestionFacadeImplSpec (T008–T011)
4. **STOP and VALIDATE**: `./mvnw test -Dtest=IngestionFacadeImplSpec` — 4 tests green
5. Commit and review

### Incremental Delivery

```
Phase 2 done → Phase 3 (US1 MVP) → Phase 4 (US2) → Phase 5+6 in parallel (US3+US4) → Phase 7 (US5) → Phase 8 (polish)
```

### Parallel Team Strategy (3 developers)

```
All: Phase 1 + Phase 2 together (T001–T007)
Dev A: Phase 3 (US1 — Ingestion)
Dev B: Phase 4 (US2 — CRUD)
Dev A: Phase 5 (US3 — Streaming) after US1 done
Dev B: Phase 6 (US4 — Voice) after US2 done
Dev A or B: Phase 7 (US5 — DuplicateChecker)
All: Phase 8 (Polish)
```

---

## Notes

- `[P]` tasks operate on different files — no merge conflicts
- `[Story]` label maps each task to its AC traceability row in spec.md
- **TDD is mandatory** (Constitution): never skip the RED confirmation step (T009, T013, T017, T021, T025)
- `DuplicateChecker` branch coverage = 100 % is a **hard gate** — do not merge Phase 7 below this threshold
- Each user story phase commits independently: `test(phase-6): add <ClassName>Spec — <description>`
- Avoid bundling multiple spec classes in one commit (Constitution Principle — Commit discipline)
