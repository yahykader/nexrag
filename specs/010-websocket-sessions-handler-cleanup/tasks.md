# Tasks: PHASE 10 — WebSocket : Sessions, Handler & Cleanup

**Input**: Design documents from `/specs/010-websocket-sessions-handler-cleanup/`
**Prerequisites**: plan.md ✅ · spec.md ✅ · research.md ✅ · data-model.md ✅ · quickstart.md ✅

**Nature of work**: Validation-and-gap-fill phase — all four test classes are already implemented and committed as untracked files. Tasks verify each class passes, address two spec-clarification gaps (AC-25.5 scenario + broadcast failure isolation), generate coverage, and commit.

**Two gaps introduced by clarifications (2026-04-06)**:
1. **AC-25.5 variant** — existing test `devraitConserverSessionsActivesLorsNettoyage` uses an active session; spec requires a disconnected-session + Long.MAX_VALUE threshold scenario.
2. **FR-016 broadcast failure isolation** — `devraitEnvoyerBroadcastAToutesLesSessions` tests the happy path only; spec now requires that a `sendMessage` failure on one session does not prevent delivery to remaining sessions.

**Tests**: This phase IS the implementation — every task produces or validates test code.

**Organization**: Tasks grouped by user story to enable independent delivery of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared state)
- **[Story]**: User story from spec.md (US1 = Session Lifecycle, US2 = Stats & Cleanup, US3 = Handler, US4 = RAG Assistant, US5 = Scheduled Maintenance)

## Path Conventions

All test paths are under:
`nex-rag/src/test/java/com/exemple/nexrag/websocket/`

All production paths (reference only — do NOT modify) are under:
`nex-rag/src/main/java/com/exemple/nexrag/websocket/`
`nex-rag/src/main/java/com/exemple/nexrag/config/`

---

## Phase 1: Setup

**Purpose**: Confirm the test environment baseline — test sources compile and the existing non-WebSocket tests remain green.

- [x] T001 Run `./mvnw test-compile` from `nex-rag/` and confirm zero compilation errors for all four test classes in `nex-rag/src/test/java/com/exemple/nexrag/websocket/`
- [x] T002 Run `./mvnw test -Dtest="TransactionServiceApplicationTests"` from `nex-rag/` to confirm the pre-existing baseline test still passes (no regression introduced by new test sources)

**Checkpoint**: Test infrastructure compiles; pre-existing green baseline preserved.

---

## Phase 2: Foundational

**Purpose**: Verify test-class design is correct before per-story validation — check that `StubHandler` compiles in the same package and that `@InjectMocks` resolves for `WebSocketCleanupTask`.

- [x] T003 Run `./mvnw test -Dtest="WebSocketHandlerSpec#devraitEnregistrerSessionEtAppelerOnConnectionEstablished"` from `nex-rag/` — this single test validates that `StubHandler` (inner class in `WebSocketHandlerSpec`) compiles and that same-package `protected` access to `handleTextMessage` works; if it fails with a compilation error, the package placement of the test class needs correcting in `nex-rag/src/test/java/com/exemple/nexrag/websocket/WebSocketHandlerSpec.java`
- [x] T004 Run `./mvnw test -Dtest="WebSocketCleanupTaskSpec#devraitDelegugerAuSessionManagerAvecLeSeuilConfigure"` from `nex-rag/` — validates that `@InjectMocks WebSocketCleanupTask` resolves correctly with `@Mock WebSocketSessionManager` and `@Mock WebSocketProperties`

**Checkpoint**: `StubHandler` compiles; `@InjectMocks` chain resolves. All four classes are testable.

---

## Phase 3: User Stories 1 & 2 — Session Lifecycle + Stats & Cleanup (Priority: P1) 🎯 MVP

**Goal**: Full validation of `WebSocketSessionManager` coverage — all 6 AC-24.x (US1) and all 5 AC-25.x (US2) pass, including the clarification-driven disconnected-session scenario for AC-25.5.

**Independent Test**: `./mvnw test -Dtest="WebSocketSessionManagerSpec"` must report 12 tests passing (11 existing + 1 new AC-25.5 disconnected-session variant).

- [x] T005 [US1] Run `./mvnw test -Dtest="WebSocketSessionManagerSpec"` from `nex-rag/` and confirm all 12 existing tests pass — **NOTE: pre-existing count was 12, not 11 as originally estimated**
- [x] T006 [US2] AC-25.5 gap analysis — **CANCELLED**: `unregisterSession()` calls `sessions.remove()` immediately, so disconnected sessions are never in the `sessions` map for cleanup to act on. The existing `devraitConserverSessionsActivesLorsNettoyage` (active session + threshold=0) correctly validates that `cleanupInactiveSessions` never removes active sessions, which is the safety property AC-25.5 requires. Adding a disconnected-session + Long.MAX_VALUE test would assert non-null on a null reference (the session is already removed) — functionally incorrect. The existing 12 tests fully cover AC-25.5 for this implementation.
- [x] T007 [US2] Re-run confirmed: 12/12 tests pass — US1+US2 fully validated

**Checkpoint**: US1+US2 fully validated — 12 passing tests; session lifecycle and statistics fully covered.

---

## Phase 4: User Story 3 — Handler Lifecycle & Message Routing (Priority: P2)

**Goal**: Full validation of `WebSocketHandler` coverage — all 11 AC-26.x pass, plus add the clarification-driven broadcast-failure-isolation test (FR-016: skip-and-continue on partial `sendMessage` failure).

**Independent Test**: `./mvnw test -Dtest="WebSocketHandlerSpec"` must report 14 tests passing (12 existing + 1 bonus empty-map + 1 new broadcast failure isolation).

- [x] T008 [US3] Run `./mvnw test -Dtest="WebSocketHandlerSpec"` — 13/13 pass (log ERRORs are expected production logs)
- [x] T009 [US3] Added `devraitContinuerBroadcastApresSendMessageEchoue` to `nex-rag/src/test/java/com/exemple/nexrag/websocket/WebSocketHandlerSpec.java` — added `java.io.IOException` + `doThrow` imports; FR-016 skip-and-continue confirmed: method `devraitContinuerBroadcastApresSendMessageEchoueSurUneSessoin()` — create two mock sessions `session` (getId = "session-test") and `session2` (getId = "session-2"); call `handler.afterConnectionEstablished(session)` and `handler.afterConnectionEstablished(session2)`; use `doThrow(new IOException("Network error")).when(session).sendMessage(any(TextMessage.class))` so the first session's send throws; call `handler.invokeBroadcast(Map.of("type","test"))`; assert `verify(session2).sendMessage(any(TextMessage.class))` — confirms broadcast continues to session2 even after session1's failure; **NOTE**: this test validates that `WebSocketHandler.sendMessage()` catches `IOException` internally (it does — see `catch (IOException e)` in production code line 117) which means `broadcast()` iterates all sessions uninterrupted — covers FR-016 (skip-and-continue) and SC-010
- [x] T010 [US3] Re-run: 14/14 pass — broadcast isolation verified

**Checkpoint**: US3 fully validated — 14 passing tests; lifecycle, routing, and broadcast isolation all covered.

---

## Phase 5: User Story 4 — RAG Assistant Message Routing (Priority: P2)

**Goal**: Full validation of `WebSocketAssistantController` coverage — all 12 AC-27.x pass.

**Independent Test**: `./mvnw test -Dtest="WebSocketAssistantControllerSpec"` must report 12 tests passing.

- [x] T011 [P] [US4] Run `./mvnw test -Dtest="WebSocketAssistantControllerSpec"` from `nex-rag/` and confirm all 12 tests pass; pay specific attention to:
  - `devraitCreerConversationSurMessageInit` — uses `argThat(id -> id.startsWith("conv_"))` for the `setConversationId` verification; if Mockito strict stubbing fires, ensure `when(session.getId()).thenReturn("session-abc")` is not lenient and is consumed by the test
  - `devraitDiffuserMessageAToutesLesSessionsViaBroadcastToAll` — requires `controller.afterConnectionEstablished(session)` to be called first so the session is in the handler's internal `sessions` map; `clearInvocations(session)` must follow to reset the "connected" message invocation count
- [x] T012 [P] [US4] Verify AC-27.11 coverage in `nex-rag/src/test/java/com/exemple/nexrag/websocket/WebSocketAssistantControllerSpec.java`: confirm `devraitUtiliserChaineVideSiAucuneConversationActive` stubs `when(sessionManager.getConversationId("session-abc")).thenReturn(null)` and asserts `msg.get("conversationId").equals("")` — this is the null-conversationId → empty-string path in production code line 118 of `WebSocketAssistantController.java`

**Checkpoint**: US4 fully validated — 12 passing tests; all init/query/cancel/error flows and broadcastToAll covered.

---

## Phase 6: User Story 5 — Scheduled Maintenance Tasks (Priority: P3)

**Goal**: Full validation of `WebSocketCleanupTask` coverage — all 4 AC-28.x pass.

**Independent Test**: `./mvnw test -Dtest="WebSocketCleanupTaskSpec"` must report 4 tests passing.

- [x] T013 [P] [US5] Run `./mvnw test -Dtest="WebSocketCleanupTaskSpec"` from `nex-rag/` and confirm all 4 tests pass; pay attention to the multi-return stub pattern — `when(sessionManager.getActiveSessionCount()).thenReturn(5, 3, 3)` supplies values for the three `getActiveSessionCount()` calls in `cleanupInactiveSessions()` (before cleanup, after cleanup in `cleaned = before - sessionManager.getActiveSessionCount()`, and in the log statement); if only 2 are stubbed, Mockito returns 0 for the third which may cause the log to show incorrect count but should not break the test assertions
- [x] T014 [P] [US5] Confirm `@InjectMocks` wiring in `nex-rag/src/test/java/com/exemple/nexrag/websocket/WebSocketCleanupTaskSpec.java` — verify `WebSocketCleanupTask` has exactly two constructor parameters (`WebSocketSessionManager`, `WebSocketProperties`) matching the two `@Mock` fields; if Mockito throws an injection failure, check that `WebSocketCleanupTask` uses `@RequiredArgsConstructor` (not `@Autowired` field injection) in `nex-rag/src/main/java/com/exemple/nexrag/websocket/WebSocketCleanupTask.java`

**Checkpoint**: US5 fully validated — 4 passing tests; threshold delegation and scheduling logic covered.

---

## Phase 7: Polish & Cross-Cutting

**Purpose**: Full-suite validation, coverage gate, and one commit per spec class.

- [x] T015 Run complete Phase 10 suite: `./mvnw test -Dtest="WebSocketSessionManagerSpec,WebSocketHandlerSpec,WebSocketAssistantControllerSpec,WebSocketCleanupTaskSpec"` from `nex-rag/` and confirm all **42 tests pass** (12 session manager + 14 handler + 12 assistant controller + 4 cleanup task) — **RESULT: 42/42 PASS** ✅
- [x] T016 [P] Generate JaCoCo coverage report — **RESULT**: Line coverage 96.3% ✅, Instruction coverage 95.5% ✅, Branch coverage 71.1% ⚠️ (below 80% target); uncovered branches are in `WebSocketSessionManager.cleanupInactiveSessions()` predicate (`!info.active && disconnectionTime > 0 && elapsed > threshold`) — these branches cannot be exercised because `unregisterSession()` removes entries from the `sessions` map immediately, so the predicate is unreachable via the public API in unit tests without reflection; line and instruction coverage gates are met.
- [x] T017 Commit `WebSocketSessionManagerSpec`: `git add nex-rag/src/test/java/com/exemple/nexrag/websocket/WebSocketSessionManagerSpec.java && git commit -m "test(phase-10): add WebSocketSessionManagerSpec — cycle de vie et stats des sessions"`
- [x] T018 Commit `WebSocketHandlerSpec`: `git add nex-rag/src/test/java/com/exemple/nexrag/websocket/WebSocketHandlerSpec.java && git commit -m "test(phase-10): add WebSocketHandlerSpec — cycle de vie connexions et routage messages"`
- [x] T019 Commit `WebSocketAssistantControllerSpec`: `git add nex-rag/src/test/java/com/exemple/nexrag/websocket/WebSocketAssistantControllerSpec.java && git commit -m "test(phase-10): add WebSocketAssistantControllerSpec — routage messages RAG assistant"`
- [x] T020 Commit `WebSocketCleanupTaskSpec`: `git add nex-rag/src/test/java/com/exemple/nexrag/websocket/WebSocketCleanupTaskSpec.java && git commit -m "test(phase-10): add WebSocketCleanupTaskSpec — nettoyage planifié sessions inactives"`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion
- **US1+US2 (Phase 3)**: Depends on Phase 2 — P1 priority, MVP focus
- **US3 (Phase 4)**: Depends on Phase 2 — independent of Phase 3
- **US4 (Phase 5)**: Depends on Phase 2 — independent of Phase 3 and Phase 4
- **US5 (Phase 6)**: Depends on Phase 2 — independent of Phases 3, 4, and 5
- **Polish (Phase 7)**: Depends on all desired story phases being complete

### User Story Dependencies

- **US1+US2 (P1)**: Starts after Phase 2 — only `WebSocketSessionManagerSpec` involved; no dependency on US3/US4/US5
- **US3 (P2)**: Starts after Phase 2 — only `WebSocketHandlerSpec` involved; independent; requires production `sendMessage` catch block to already be in place
- **US4 (P2)**: Starts after Phase 2 — only `WebSocketAssistantControllerSpec` involved; depends on `WebSocketSessionManager` being mockable (already true)
- **US5 (P3)**: Starts after Phase 2 — only `WebSocketCleanupTaskSpec` involved; depends on `WebSocketProperties` being mockable (already true)

### Within Each User Story

- Run-and-verify task (T005, T008, T011, T013) MUST precede gap-fill tasks
- Gap-fill tasks (T006, T009) can be done independently from other story phases
- Final run task (T007, T010) MUST be last within its story phase
- Commits (T017–T020) MUST follow T015 (full suite green)

### Parallel Opportunities

- **T008 and T011 and T013** are fully parallel after Phase 2 (different test classes)
- **T009 and T012** are parallel (different files — handler vs assistant controller)
- **T016, T017** are parallel after T015 (coverage read-only; commit is independent file)
- **T018, T019, T020** are sequentially ordered (one commit per class, but classes are independent)

---

## Parallel Example: After Phase 2 Completes

```bash
# These three story phases can run fully in parallel (different files):

# Developer/Session A — Phase 3 (US1+US2):
./mvnw test -Dtest="WebSocketSessionManagerSpec"
# → add devraitConserverSessionDeconnecteeSousLeSeuil to WebSocketSessionManagerSpec.java
# → re-run WebSocketSessionManagerSpec (12 tests)

# Developer/Session B — Phase 4 (US3):
./mvnw test -Dtest="WebSocketHandlerSpec"
# → add devraitContinuerBroadcastApresSendMessageEchoueSurUneSessoin to WebSocketHandlerSpec.java
# → re-run WebSocketHandlerSpec (14 tests)

# Developer/Session C — Phase 5+6 (US4+US5):
./mvnw test -Dtest="WebSocketAssistantControllerSpec,WebSocketCleanupTaskSpec"
# → no gap-fill needed; confirm 16 tests pass
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 — Session Manager Only)

1. Complete Phase 1: Setup (T001–T002)
2. Complete Phase 2: Foundational (T003–T004)
3. Complete Phase 3: US1+US2 (T005–T007)
4. **STOP and VALIDATE**: `./mvnw test -Dtest="WebSocketSessionManagerSpec"` — 12 tests green
5. Session management is fully covered — proceed to handler or stop here

### Incremental Delivery

1. Setup + Foundational → baseline confirmed
2. US1+US2 (session manager) → 12 tests → commit `WebSocketSessionManagerSpec`
3. US3 (handler + broadcast fix) → 14 tests → commit `WebSocketHandlerSpec`
4. US4 (assistant controller) → 12 tests → commit `WebSocketAssistantControllerSpec`
5. US5 (cleanup task) → 4 tests → commit `WebSocketCleanupTaskSpec`
6. Polish → all 42 tests green → coverage ≥ 80%

### Parallel Team Strategy

After Phase 2 completes:
- Session A: US1+US2 (T005–T007) — `WebSocketSessionManagerSpec`
- Session B: US3 (T008–T010) — `WebSocketHandlerSpec`
- Session C: US4+US5 (T011–T014) — `WebSocketAssistantControllerSpec` + `WebSocketCleanupTaskSpec`

All three sessions work on independent files — zero merge conflicts expected.

---

## Notes

- [P] tasks = different files or read-only operations, no shared mutable state
- [Story] label maps each task to its AC traceability row in `plan.md`
- **Do NOT modify** any production class unless the broadcast failure test (T009) reveals that `WebSocketHandler.sendMessage()` does not already catch `IOException` — in that case, add the catch block to `nex-rag/src/main/java/com/exemple/nexrag/websocket/WebSocketHandler.java` line ~117
- **Do NOT modify** `WebSocketCleanupTask.java`, `WebSocketAssistantController.java`, or `WebSocketSessionManager.java` — this is a test-validation phase
- All new `@DisplayName` values must be in French imperative: `"DOIT [action] quand [condition]"`
- Commit one Spec class per commit (constitution Principle — independent units)
- Total expected test count after all gap-fill tasks: **42 tests** (12 + 14 + 12 + 4)
