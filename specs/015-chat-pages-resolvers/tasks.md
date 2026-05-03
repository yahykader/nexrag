# Tasks: Phase 8 — Chat Pages & Resolvers Test Coverage

**Input**: Design documents from `/specs/015-chat-pages-resolvers/`  
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓, quickstart.md ✓

**Note**: This phase delivers test code as its primary artifact. Every task produces either a production file change required for testability (Phases 1–2) or a fully-implemented `it()` block (Phases 3–7). Stub-only test bodies are non-conforming per Constitution Principle IX.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared state)
- **[Story]**: User story this task belongs to ([US1]–[US4])

---

## Phase 1: Setup (Production Changes for Testability)

**Purpose**: Create and refactor the one production file that blocks all test writing. Must complete before any spec file is written.

**⚠️ CRITICAL**: T002 depends on T001. T001 must complete before T002 begins.

- [x] T001 Create `ConfirmationService` with a single `confirm(message: string): boolean` method wrapping `window.confirm` in `agentic-rag-ui/src/app/core/services/confirmation.service.ts`
- [x] T002 Inject `ConfirmationService` into `ChatPageComponent` constructor and replace the direct `window.confirm` call in `onDeleteConversation` with `this.confirmationService.confirm(...)` in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.ts`

**Checkpoint**: Run `npm run build` (or `npm start`) to confirm no compile errors before writing any tests.

---

## Phase 2: Foundational (Spec File Scaffolding)

**Purpose**: Create the two spec file shells with full test setup — `describe` blocks, `beforeEach`, test data factories, and `ChatInterfaceStub` — but with empty `it()` bodies. Confirms the test harness compiles and providers resolve before any assertions are written.

**⚠️ CRITICAL**: Both T001 and T002 (Phase 1) must be complete before T004 (page spec) can compile. T003 (resolver spec) can start immediately after T001.

- [x] T003 [P] Scaffold `chat.resolver.spec.ts` with `createServiceFactory`, `provideMockStore`, `mockChatState` helper, and 3 empty `it()` shells (labels in French imperative) at `agentic-rag-ui/src/app/features/chat/resolvers/chat.resolver.spec.ts`
- [x] T004 [P] Scaffold `chat-page.component.spec.ts` with `createComponentFactory`, `ChatInterfaceStub`, `mockProvider(ConfirmationService)`, `mockConversation` + `mockChatState` helpers, and 13 empty `it()` shells + 2 `[INTÉGRATION]` shells at `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`

> **TDD gate**: Run `npm test` — all new `it()` shells should either fail with "no assertions" or be pending. Confirm the spec files compile without TypeScript errors.

**Checkpoint**: Both spec files compile and appear in the test runner output before moving to Phase 3.

---

## Phase 3: User Story 1 — Resolver Guard Tests (Priority: P1) 🎯 MVP

**Goal**: Verify that `ChatResolver` correctly dispatches `createConversation` when needed, skips it when a conversation already exists, and always completes its observable.

**Independent Test**: Run `npm test -- --include="**/resolvers/**"` and confirm 3 tests pass.

- [x] T005 [P] [US1] Implement `it('doit dispatcher createConversation quand aucune conversation active')` — set store state with `activeConversationId: null`, call `resolver.resolve()`, assert `store.dispatch` called with `createConversation()` in `agentic-rag-ui/src/app/features/chat/resolvers/chat.resolver.spec.ts`
- [x] T006 [P] [US1] Implement `it('ne doit pas dispatcher createConversation quand une conversation est déjà active')` — set store state with `activeConversationId: 'conv-1'`, call `resolver.resolve()`, assert `store.dispatch` NOT called in `agentic-rag-ui/src/app/features/chat/resolvers/chat.resolver.spec.ts`
- [x] T007 [P] [US1] Implement `it('doit émettre une fois et compléter sans bloquer la navigation')` — subscribe to `resolver.resolve()`, assert `emitCount === 1` and `completed === true` using next/complete callbacks in `agentic-rag-ui/src/app/features/chat/resolvers/chat.resolver.spec.ts`

**Checkpoint**: `npm test -- --include="**/resolvers/**"` → 3 passing. User Story 1 is fully verified.

---

## Phase 4: User Story 2 — Chat Page Store Rendering Tests (Priority: P1)

**Goal**: Verify that `ChatPageComponent` subscribes to store observables, renders the conversation list and empty state correctly, and includes `app-chat-interface` in its template.

**Independent Test**: Run `npm test -- --include="**/chat-page/**"` with only US2 `it()` blocks implemented and confirm 5 pass.

- [x] T008 [P] [US2] Implement `it('doit créer le composant')` — assert `spectator.component` is truthy in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`
- [x] T009 [P] [US2] Implement `it('doit s\'abonner à conversations$ depuis le store')` — assert `spectator.component.conversations$` is an Observable (use `instanceof Observable` or `isObservable`) in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`
- [x] T010 [P] [US2] Implement `it('doit s\'abonner à activeConversationId$ depuis le store')` — assert `spectator.component.activeConversationId$` is an Observable in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`
- [x] T011 [P] [US2] Implement `it('doit afficher deux items quand le store contient deux conversations')` — set store state with 2 conversations via `mockStore.setState`, call `spectator.detectChanges()`, assert `spectator.queryAll('.conversation-item').length === 2` in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`
- [x] T012 [P] [US2] Implement `it('doit afficher l\'état vide quand conversations est vide')` — set store state with empty conversations, call `spectator.detectChanges()`, assert `.empty-conversations` element is present in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`
- [x] T013 [P] [US2] Implement `it('doit inclure app-chat-interface dans le template')` — assert `spectator.query('app-chat-interface')` is not null in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`

**Checkpoint**: `npm test -- --include="**/chat-page/**"` → 5+ passing (US2 tests). User Story 2 is fully verified.

---

## Phase 5: User Story 3 — Conversation Management Action Tests (Priority: P2)

**Goal**: Verify that each sidebar interaction (create, select, delete-confirm, delete-cancel) dispatches exactly the right NgRx action.

**Independent Test**: Run full chat-page spec — the 4 US3 `it()` blocks pass alongside the 6 already-passing US2 tests.

- [x] T014 [P] [US3] Implement `it('doit dispatcher createConversation au click sur Nouveau')` — call `spectator.click('.btn-primary')` (the Nouveau button), assert `store.dispatch` called with `createConversation()` in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`
- [x] T015 [P] [US3] Implement `it('doit dispatcher setActiveConversation avec le bon id au click sur une conversation')` — seed 1 conversation, click `.conversation-item`, assert `store.dispatch` called with `setActiveConversation({ conversationId: 'conv-1' })` in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`
- [x] T016 [P] [US3] Implement `it('doit dispatcher deleteConversation quand ConfirmationService retourne true')` — configure `mockProvider(ConfirmationService, { confirm: () => true })`, seed 1 conversation, click the delete button, assert `store.dispatch` called with `deleteConversation({ conversationId: 'conv-1' })` in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`
- [x] T017 [P] [US3] Implement `it('ne doit pas dispatcher deleteConversation quand ConfirmationService retourne false')` — configure `mockProvider(ConfirmationService, { confirm: () => false })`, click the delete button, assert `store.dispatch` NOT called with `deleteConversation` in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`

**Checkpoint**: 10 passing tests in chat-page spec. User Story 3 is fully verified.

---

## Phase 6: User Story 4 — Sidebar Toggle Tests (Priority: P3)

**Goal**: Verify that `toggleSidebar()` correctly flips `sidebarCollapsed` and that the `collapsed` CSS class is applied and removed.

**Independent Test**: The 2 US4 `it()` blocks pass alongside all prior tests.

- [x] T018 [P] [US4] Implement `it('doit passer sidebarCollapsed à true au premier appel de toggleSidebar()')` — call `spectator.component.toggleSidebar()`, `spectator.detectChanges()`, assert `spectator.component.sidebarCollapsed === true` and `spectator.query('.chat-sidebar')` has class `collapsed` in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`
- [x] T019 [P] [US4] Implement `it('doit passer sidebarCollapsed à false au deuxième appel de toggleSidebar()')` — call `toggleSidebar()` twice, assert `sidebarCollapsed === false` and `collapsed` class is absent in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`

**Checkpoint**: 12 passing unit tests in chat-page spec. User Story 4 verified.

---

## Phase 7: Integration Tests

**Goal**: Verify the full page component tree and end-to-end dispatch flow with a non-shallow render and a real `MockStore`.

**Independent Test**: 2 additional `[INTÉGRATION]` tests pass alongside all 12 unit tests.

- [x] T020 [US2] Implement `it('[INTÉGRATION] doit afficher l\'élément app-chat-interface dans le template complet')` — use non-shallow `createComponentFactory` (without `shallow: true`), seed store with one conversation, assert `spectator.query('app-chat-interface')` is not null and is within `.chat-main` in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`
- [x] T021 [US3] Implement `it('[INTÉGRATION] dispatch complet : click conversation → setActiveConversation dispatché')` — seed store with 2 conversations, click the first `.conversation-item`, assert `MockStore.scannedActions$` contains `setActiveConversation({ conversationId: 'conv-1' })` in `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.spec.ts`

**Checkpoint**: 16 total tests pass (14 unit + 2 integration). Complete Phase 8 test suite green.

---

## Phase 8: Polish & Verification

**Purpose**: Coverage validation, commit, and cross-cutting checks.

- [x] T022 Run full Phase 8 test suite with coverage and verify SC-003 targets (statements ≥ 80 %, branches ≥ 75 %, functions ≥ 85 %) using `npm test -- --coverage --include="**/chat/pages/**" --include="**/chat/resolvers/**"` in `agentic-rag-ui/`
- [ ] T023 [P] Commit production changes with message `refactor(chat-page): extract window.confirm into ConfirmationService for test isolation`
- [ ] T024 [P] Commit test files with message `test(phase-8): add chat-page.component.spec + chat.resolver.spec — page lifecycle & resolver guard`

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (T001 → T002)         ← no dependencies; start here
    ↓
Phase 2 (T003, T004) [P]       ← T004 requires Phase 1 complete; T003 only requires T001
    ↓
Phase 3 (T005–T007) [P]        ← depends on T003 (resolver spec scaffolded)
Phase 4 (T008–T013) [P]        ← depends on T004 (page spec scaffolded)
Phase 5 (T014–T017) [P]        ← depends on T004 + Phase 1 complete
Phase 6 (T018–T019) [P]        ← depends on T004
    ↓
Phase 7 (T020–T021)            ← depends on all unit tests passing (Phases 3–6)
    ↓
Phase 8 (T022–T024)            ← depends on Phase 7 complete
```

### User Story Dependencies

| Story | Depends on | Blocks |
|---|---|---|
| US1 (Resolver) | Phase 2 (T003) | Nothing |
| US2 (Page Rendering) | Phase 2 (T004) | T020 (integration) |
| US3 (Management) | Phase 2 (T004) + Phase 1 | T021 (integration) |
| US4 (Sidebar) | Phase 2 (T004) | Nothing |

**US1 and US2/US3/US4 are independent** — resolver spec and page spec live in different files.

### Parallel Opportunities

```bash
# Phase 1: sequential (T002 depends on T001)
# T001 → T002

# Phase 2: parallel (different files)
# T003 (resolver spec scaffold)  ←→  T004 (page spec scaffold)

# Phase 3 (all in resolver spec — independent it() blocks):
# T005  ←→  T006  ←→  T007

# Phase 4 (all in page spec — independent it() blocks):
# T008  ←→  T009  ←→  T010  ←→  T011  ←→  T012  ←→  T013

# Phase 5 (all in page spec — independent it() blocks):
# T014  ←→  T015  ←→  T016  ←→  T017

# Phase 6 (all in page spec — independent it() blocks):
# T018  ←→  T019

# Phase 7: sequential (integration tests build on full spec)
# Phase 8: T023 ←→ T024 (parallel commits)
```

---

## Implementation Strategy

### MVP First (User Story 1 — Resolver Only)

1. Complete Phase 1: T001, T002 (production changes)
2. Complete T003 (resolver spec scaffold)
3. Complete Phase 3: T005, T006, T007 (resolver tests)
4. **STOP and VALIDATE**: `npm test -- --include="**/resolvers/**"` → 3 passing
5. The route guard is now fully verified — independently deliverable

### Incremental Delivery

1. Phase 1 + T003 → Resolver spec green (3 tests)
2. T004 → Page spec scaffold compiles
3. Phase 4 → 5 rendering tests green (8 total)
4. Phase 5 → 4 management tests green (12 total)
5. Phase 6 → 2 sidebar tests green (14 total)
6. Phase 7 → 2 integration tests green (16 total)
7. Phase 8 → Coverage gate passed, committed

### Single-Developer Flow

```
T001 → T002 → T003 → T005 → T006 → T007  [resolver done]
                  ↘
                   T004 → T008 → T009 → T010 → T011 → T012 → T013
                          → T014 → T015 → T016 → T017
                          → T018 → T019
                          → T020 → T021
                          → T022 → T023 → T024
```

---

## Notes

- `[P]` tasks within the same spec file are logically independent `it()` blocks and can be written in any order
- All `it()` labels MUST be in French imperative — `[INTÉGRATION]` prefix for integration tests (Constitution VIII)
- Use `mockStore.setState({ chat: mockChatState(...) })` + `spectator.detectChanges()` between tests that need different store state
- The `ChatInterfaceStub` stub must be declared OUTSIDE the `describe` block (module-level) to be usable in `overrideComponents`
- `store.dispatch` spy: use `vi.spyOn(store, 'dispatch')` or inject `MockStore` and check `scannedActions$`
- Run `npm test` after every phase checkpoint before proceeding
