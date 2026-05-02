# Tasks: Chat Store NgRx Tests (Phase 6)

**Input**: Design documents from `specs/013-chat-store-ngrx/`  
**Prerequisites**: plan.md ✅ · spec.md ✅ · research.md ✅ · data-model.md ✅ · contracts/ ✅ · quickstart.md ✅

**Tests**: This feature IS the test suite — every task produces `*.spec.ts` files.  
**TDD flow**: Write `it()` blocks → confirm RED → (production code already exists) → confirm GREEN → verify coverage.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies between those tasks)
- **[Story]**: User story this task belongs to (US1=Reducer, US2=Selectors, US3=Effects, US4=Actions)

## Path Convention

All spec files are co-located in:  
`agentic-rag-ui/src/app/features/chat/store/`

---

## Phase 1: Setup (Verify Test Infrastructure)

**Purpose**: Confirm all required testing packages are present and Vitest coverage is configured before writing any spec.

- [x] T001 Verify `@ngrx/store/testing` and `@ngrx/effects/testing` are listed in devDependencies in `agentic-rag-ui/package.json`
- [x] T002 Verify Vitest coverage configuration in `agentic-rag-ui/vitest.config.ts` (or `vite.config.ts`) includes the chat store files and enforces the coverage thresholds from quickstart.md (statements ≥80%, branches ≥75%, functions ≥85%, lines ≥80%)

**Checkpoint**: If T001 or T002 fail, install missing packages (`npm install --save-dev @ngrx/store/testing @ngrx/effects/testing`) and update the Vitest config before proceeding.

---

## Phase 2: Foundational (Shared Test Helpers)

**Purpose**: Shared state-building factories used by reducer, selector, and effects specs. Must exist before US1/US2/US3 begin.

**⚠️ CRITICAL**: US1, US2, and US3 all depend on `buildChatState()` and `mockConversation()`. Complete this phase first.

- [x] T003 Create shared test helper factories in `agentic-rag-ui/src/app/test-helpers.ts`:
  - `buildChatState(overrides?: Partial<ChatState>): { chat: ChatState }` — builds a full AppState using `conversationsAdapter.getInitialState()` for the conversations slice; applies any overrides
  - `mockMessage(overrides?: Partial<Message>): Message` — returns a valid Message with id `'msg-1'`, role `'user'`, content `'Test message'`, status `'complete'`, timestamp `new Date()`
  - `mockConversation(overrides?: Partial<Conversation>): Conversation` — returns a valid Conversation with id `'conv-1'`, title `'Test conversation'`, messages `[]`, createdAt/updatedAt `new Date()`
  - Export all three from `test-helpers.ts`

**Checkpoint**: `test-helpers.ts` must compile without errors before US1/US2/US3 begin.

---

## Phase 3: User Story 1 — Reducer State Correctness (Priority: P1) 🎯 MVP

**Goal**: Verify all 15 `on()` handlers in `chatReducer` produce the correct next state for every action.

**Independent Test**: Run `npm test -- --include="**/chat.reducer.spec.ts"` from `agentic-rag-ui/` — all tests pass and `chat.reducer.ts` reaches ≥80% statement coverage.

- [x] T004 [US1] Create `agentic-rag-ui/src/app/features/chat/store/chat.reducer.spec.ts`:
  - Add imports: `chatReducer` from `./chat.reducer`; `initialChatState`, `conversationsAdapter`, `ChatState` from `./chat.state`; all of `* as ChatActions` from `./chat.actions`; `buildChatState`, `mockMessage`, `mockConversation` from `../../../test-helpers`
  - Wrap everything in `describe('ChatReducer', () => { ... })`
  - Add `beforeEach(() => { vi.spyOn(console, 'log').mockImplementation(() => {}); })` to suppress the `console.log` calls inside `streamComplete`
  - Add `afterEach(() => { vi.restoreAllMocks(); })`

- [x] T005 [US1] Add initial state test in `chat.reducer.spec.ts`:
  - `it('doit retourner l\'état initial', ...)` — call `chatReducer(undefined, { type: '@@INIT' })`, assert result deep-equals `initialChatState`

- [x] T006 [US1] Add conversation lifecycle tests in `chat.reducer.spec.ts`:
  - `it('createConversation doit ajouter une conversation et la définir comme active', ...)` — dispatch `ChatActions.createConversation()`, assert `conversations.ids.length === 1` and `activeConversationId` matches the new id
  - `it('loadConversationsSuccess doit remplacer toutes les conversations existantes', ...)` — start from a state that already has one conversation; dispatch `loadConversationsSuccess` with two different conversations; assert the list has exactly 2 entries and the previous one is gone (replace semantics — Q1)
  - `it('deleteConversation doit retirer la conversation et effacer activeConversationId si elle était active', ...)` — dispatch `deleteConversation({ conversationId: 'conv-1' })` against a state where `conv-1` is active; assert it is removed and `activeConversationId` is null

- [x] T007 [US1] Add message creation tests in `chat.reducer.spec.ts`:
  - `it('addUserMessage doit ajouter le message à la fin de la conversation active', ...)` — use a state with one active conversation; dispatch `addUserMessage({ message: mockMessage() })`; assert `messages.length === 1` and the message matches
  - `it('addUserMessage doit définir le titre depuis le premier message si la conv est vide', ...)` — assert title equals `mockMessage().content.substring(0, 50)` after first addUserMessage
  - `it('addAssistantMessage doit ajouter un message vide avec status streaming et passer isStreaming à true', ...)` — dispatch `addAssistantMessage({ messageId: 'asst-1' })`; assert new message has `role: 'assistant'`, `content: ''`, `status: 'streaming'`; assert `isStreaming === true` and `streamingMessageId === 'asst-1'`

- [x] T008 [US1] Add streaming token tests in `chat.reducer.spec.ts`:
  - `it('streamToken doit accumuler le texte dans le message en cours de streaming', ...)` — start from a state with `isStreaming: true`, `streamingMessageId: 'asst-1'`, and an assistant message with `content: 'Hello'`; dispatch `streamToken({ event: { type: 'token', text: ' World' } })`; assert content is `'Hello World'`
  - `it('streamToken doit être ignoré si streamingMessageId est null', ...)` — start from a state with `streamingMessageId: null`; dispatch `streamToken`; assert state is unchanged (late-chunk guard — Q3)

- [x] T009 [US1] Add streaming completion and error tests in `chat.reducer.spec.ts`:
  - `it('streamComplete doit marquer le message complete, extraire les citations, et passer isStreaming à false', ...)` — dispatch `streamComplete({ event: { type: 'complete', response: { sources: [{ file: 'doc.pdf', page: '1' }] } } })`; assert last message `status === 'complete'`, `citations.length === 1`, `citations[0].sourceFile === 'doc.pdf'`; assert `isStreaming === false`, `streamingMessageId === null`
  - `it('streamError doit passer isStreaming à false, stocker l\'erreur, sans retry', ...)` — dispatch `streamError({ error: 'Connection lost' })`; assert `isStreaming === false`, `streamingMessageId === null`, `error === 'Connection lost'`
  - `it('cancelStreamSuccess doit réinitialiser tous les champs de streaming', ...)` — dispatch `cancelStreamSuccess()`; assert `isStreaming === false`, `streamingMessageId === null`, `streamSessionId === null`

- [x] T010 [US1] Add input and error tests; validate in `chat.reducer.spec.ts`:
  - `it('clearInputText doit remettre inputText à une chaîne vide', ...)` — start from state with `inputText: 'some query'`; dispatch `clearInputText()`; assert `inputText === ''`
  - `it('clearError doit remettre error à null', ...)` — start from state with `error: 'previous error'`; dispatch `clearError()`; assert `error === null`
  - Run `npm test -- --include="**/chat.reducer.spec.ts"` from `agentic-rag-ui/`; confirm all tests are GREEN; run with `--coverage` and confirm `chat.reducer.ts` meets the coverage gates

**Checkpoint**: `chat.reducer.spec.ts` is fully passing. US1 is independently deliverable.

---

## Phase 4: User Story 2 — Selector Data Access (Priority: P2)

**Goal**: Verify every exported selector from `chat.selectors.ts` returns the correct projected value for any valid state shape.

**Independent Test**: Run `npm test -- --include="**/chat.selectors.spec.ts"` — all tests pass and `chat.selectors.ts` reaches ≥80% statement coverage.

- [x] T011 [US2] Create `agentic-rag-ui/src/app/features/chat/store/chat.selectors.spec.ts`:
  - Add imports: all named selectors from `./chat.selectors`; `conversationsAdapter`, `ChatState` from `./chat.state`; `buildChatState`, `mockConversation`, `mockMessage` from `../../../test-helpers`
  - Define local helper `stateWithActiveConversation(msgs: Message[] = [])` — builds a state with one conversation containing `msgs`, sets it as active
  - Wrap in `describe('ChatSelectors', () => { ... })`

- [x] T012 [US2] Add default (empty) state selector tests in `chat.selectors.spec.ts`:
  - `it('selectConversations doit retourner un tableau vide pour l\'état initial', ...)` — `expect(selectConversations(buildChatState())).toEqual([])`
  - `it('selectActiveConversationId doit retourner null pour l\'état initial', ...)` 
  - `it('selectIsStreaming doit retourner false pour l\'état initial', ...)`
  - `it('selectError doit retourner null pour l\'état initial', ...)`
  - `it('selectInputText doit retourner une chaîne vide pour l\'état initial', ...)`

- [x] T013 [US2] Add active conversation selector tests in `chat.selectors.spec.ts`:
  - `it('selectActiveMessages doit retourner [] si aucune conversation n\'est active', ...)` — use `buildChatState({ activeConversationId: null })`; assert result is `[]`
  - `it('selectActiveMessages doit retourner les messages de la conversation active', ...)` — use `stateWithActiveConversation([mockMessage()])`; assert `length === 1`
  - `it('selectActiveConversation doit retourner la conversation active ou undefined', ...)` — test both populated and empty cases
  - `it('selectConversationsDictionary doit retourner un objet keyed par id', ...)` — use state with one conversation; assert dictionary key equals conversation id

- [x] T014 [US2] Add derived and parameterized selector tests; validate in `chat.selectors.spec.ts`:
  - `it('selectRecentConversations doit retourner au maximum 5 conversations', ...)` — build state with 7 conversations; assert `selectRecentConversations` returns exactly 5
  - `it('selectConversationById doit retourner la conversation si elle existe', ...)` — assert found vs. `undefined` for unknown id
  - Run `npm test -- --include="**/chat.selectors.spec.ts"` from `agentic-rag-ui/`; confirm GREEN; verify coverage gates

**Checkpoint**: `chat.selectors.spec.ts` is fully passing. US2 is independently deliverable.

---

## Phase 5: User Story 3 — Effects Async Orchestration (Priority: P3)

**Goal**: Verify `sendMessage$`, `startStreaming$`, and `cancelStream$` dispatch the correct action sequences for all code paths.

**Independent Test**: Run `npm test -- --include="**/chat.effects.spec.ts"` — all tests pass and `chat.effects.ts` reaches ≥80% statement coverage.

- [x] T015 [US3] Create `agentic-rag-ui/src/app/features/chat/store/chat.effects.spec.ts` with full DI setup:
  - Imports: `createServiceFactory`, `SpectatorService` from `@ngneat/spectator`; `provideMockActions` from `@ngrx/effects/testing`; `provideMockStore`, `MockStore` from `@ngrx/store/testing`; `ReplaySubject`, `of`, `throwError` from `rxjs`; `ChatEffects` from `./chat.effects`; all `* as ChatActions`; `StreamingApiService`; `buildChatState`, `mockConversation`, `mockMessage`
  - Declare `let actions$: ReplaySubject<any>` in the describe scope; reset in `beforeEach(() => { actions$ = new ReplaySubject(1); })`
  - Use `createServiceFactory` with `providers: [provideMockActions(() => actions$), provideMockStore({ initialState: buildChatState() })]` and `mocks: [StreamingApiService]`
  - Wrap in `describe('ChatEffects', () => { ... })`

- [x] T016 [US3] Add `sendMessage$` tests in `chat.effects.spec.ts`:
  - `it('doit dispatcher createConversation si aucune conversation n\'est active', ...)` — set store state with `activeConversationId: null`; dispatch `sendMessage({ content: 'Hello' })`; collect emitted actions; assert first dispatched action is `createConversation()`
  - `it('doit dispatcher addUserMessage, addAssistantMessage et clearInputText si une conversation est active', ...)` — override store with `activeConversationId: 'conv-1'` via `MockStore.overrideSelector(selectActiveConversationId, 'conv-1')`; dispatch `sendMessage({ content: 'Hello' })`; collect actions; assert they include `addUserMessage`, `addAssistantMessage`, `clearInputText` in that order

- [x] T017 [US3] Add `startStreaming$` happy path tests in `chat.effects.spec.ts`:
  - Set up store with an active conversation containing one user message; mock `streamingApi.stream()` to return `of(connectedEvent, tokenEvent, completeEvent)` where each is a valid `StreamEvent`
  - `it('doit appeler streamingApi.stream() avec la query et le conversationId', ...)` — assert `streamingApi.stream` was called with `{ query: lastUserMessage.content, conversationId: 'conv-1' }`
  - `it('doit dispatcher streamConnected pour l\'événement connected', ...)` — assert `streamConnected` dispatched
  - `it('doit dispatcher streamToken pour chaque token reçu', ...)` — assert `streamToken` dispatched with the token event
  - `it('doit dispatcher streamComplete pour l\'événement complete', ...)` — assert `streamComplete` dispatched

- [x] T018 [US3] Add `startStreaming$` failure tests in `chat.effects.spec.ts`:
  - `it('doit dispatcher streamError si streamingApi.stream() lève une erreur (catchError)', ...)` — mock `streamingApi.stream()` to return `throwError(() => new Error('Network error'))`; assert `streamError({ error: 'Network error' })` is dispatched; assert no retry occurs (only one call to `stream()`)
  - `it('doit dispatcher streamError si aucun message utilisateur n\'est trouvé dans la conversation', ...)` — set active conversation with empty `messages[]`; dispatch `addAssistantMessage({ messageId: 'asst-1' })`; assert `streamError` dispatched

- [x] T019 [US3] Add `cancelStream$` tests; validate in `chat.effects.spec.ts`:
  - `it('doit dispatcher cancelStreamSuccess directement si streamSessionId est null', ...)` — set store state `streamSessionId: null`; dispatch `cancelStream()`; assert `cancelStreamSuccess()` emitted without calling `streamingApi.cancelStream()`
  - Run `npm test -- --include="**/chat.effects.spec.ts"` from `agentic-rag-ui/`; confirm GREEN; verify coverage gates

**Checkpoint**: `chat.effects.spec.ts` is fully passing. US3 is independently deliverable.

---

## Phase 6: User Story 4 — Action Type and Payload Safety (Priority: P4)

**Goal**: Verify all 17 action creators produce objects with the correct type string and exact payload shape as documented in `contracts/action-types.md`.

**Independent Test**: Run `npm test -- --include="**/chat.actions.spec.ts"` — all tests pass. This spec has no production dependencies; it can be done as a warm-up before or after any other phase.

- [x] T020 [US4] Create `agentic-rag-ui/src/app/features/chat/store/chat.actions.spec.ts`:
  - Import `* as ChatActions` from `./chat.actions`; import `Message` and `Conversation` from `./chat.state`
  - Wrap in `describe('ChatActions', () => { ... })`
  - Add `describe` sub-groups for conversation, message, streaming, and input/error actions

- [x] T021 [US4] Add conversation action type tests in `chat.actions.spec.ts`:
  - `it('createConversation doit avoir le type [Chat] Create Conversation sans payload', ...)` — assert `ChatActions.createConversation().type === '[Chat] Create Conversation'`
  - `it('loadConversationsSuccess doit inclure le tableau conversations dans le payload', ...)` — create with `{ conversations: [] }`; assert `action.type` and `action.conversations`
  - `it('setActiveConversation doit inclure conversationId dans le payload', ...)` — create with `{ conversationId: 'c1' }`; assert `action.conversationId === 'c1'`
  - `it('deleteConversation doit inclure conversationId dans le payload', ...)`

- [x] T022 [US4] Add message action type tests in `chat.actions.spec.ts`:
  - `it('sendMessage doit avoir le type [Chat] Send Message et le payload content', ...)` — create with `{ content: 'Bonjour' }`; assert `action.type === '[Chat] Send Message'` and `action.content === 'Bonjour'`
  - `it('addUserMessage doit inclure le message complet dans le payload', ...)` — create with a `mockMessage()`; assert `action.message` matches
  - `it('addAssistantMessage doit inclure messageId dans le payload', ...)` — create with `{ messageId: 'asst-1' }`; assert `action.messageId === 'asst-1'`

- [x] T023 [US4] Add streaming and input action type tests; validate in `chat.actions.spec.ts`:
  - `it('streamToken doit encapsuler le StreamEvent dans event', ...)` — create with `{ event: { type: 'token', text: 'hello' } as any }`; assert `action.event.text === 'hello'`
  - `it('streamError doit avoir error dans le payload', ...)` — create with `{ error: 'fail' }`; assert type and error
  - `it('cancelStream doit avoir le type [Chat] Cancel Stream sans payload', ...)`
  - `it('clearInputText doit avoir le type [Chat] Clear Input Text sans payload', ...)`
  - Run `npm test -- --include="**/chat.actions.spec.ts"` from `agentic-rag-ui/`; confirm GREEN

**Checkpoint**: `chat.actions.spec.ts` is fully passing. US4 is independently deliverable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final validation, coverage gate enforcement, and per-file commits per constitution.

- [x] T024 Run the full Phase 6 suite with coverage from `agentic-rag-ui/`: `npm test -- --coverage --include="**/features/chat/store/**"` — verify all 4 coverage gates pass across all 4 spec files
- [ ] T025 [P] Commit `chat.actions.spec.ts` with message: `test(phase-6): add chat.actions.spec — types et payloads des actions [Chat]`
- [ ] T026 [P] Commit `chat.reducer.spec.ts` with message: `test(phase-6): add chat.reducer.spec — transitions d'état et gardes du reducer`
- [ ] T027 [P] Commit `chat.selectors.spec.ts` with message: `test(phase-6): add chat.selectors.spec — projections NgRx selectActiveMessages et sélecteurs dérivés`
- [ ] T028 [P] Commit `chat.effects.spec.ts` with message: `test(phase-6): add chat.effects.spec — orchestration streaming sendMessage$ et startStreaming$`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — blocks US1, US2, US3 (not US4)
- **US1 — Reducer (Phase 3)**: Depends on Phase 2 (needs `buildChatState`, `mockConversation`)
- **US2 — Selectors (Phase 4)**: Depends on Phase 2 (needs `buildChatState`, `mockConversation`)
- **US3 — Effects (Phase 5)**: Depends on Phase 2 (needs `buildChatState`, `mockConversation`)
- **US4 — Actions (Phase 6)**: No dependency on Phase 2 — can run in parallel with Phases 3–5
- **Polish (Phase 7)**: Depends on all spec files being complete

### User Story Dependencies

- **US1 (P1)** — independent after Phase 2
- **US2 (P2)** — independent after Phase 2; no dependency on US1
- **US3 (P3)** — independent after Phase 2; no dependency on US1 or US2
- **US4 (P4)** — fully independent; can start immediately after Phase 1

### Parallel Opportunities

Once Phase 2 is done, all 4 user stories can be worked in parallel (different files, zero cross-dependencies):
- Developer A: US1 (`chat.reducer.spec.ts`)
- Developer B: US2 (`chat.selectors.spec.ts`)
- Developer C: US3 (`chat.effects.spec.ts`)
- Developer D / same dev early: US4 (`chat.actions.spec.ts`)

---

## Parallel Example: All 4 User Stories (if staffed)

```bash
# After Phase 2 completes, these can all run simultaneously:
Task A: "Write chat.reducer.spec.ts — 10 it() blocks for US1"
Task B: "Write chat.selectors.spec.ts — 8 it() blocks for US2"
Task C: "Write chat.effects.spec.ts — 7 it() blocks for US3"
Task D: "Write chat.actions.spec.ts — 9 it() blocks for US4"
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Complete Phase 1 (Setup)
2. Complete Phase 2 (test-helpers.ts)
3. Complete Phase 3 (chat.reducer.spec.ts)
4. **STOP and VALIDATE**: `npm test -- --coverage --include="**/chat.reducer.spec.ts"` passes all gates
5. Commit US1 independently

### Incremental Delivery

1. Phase 1 + Phase 2 → helpers ready
2. US4 (actions) — fastest spec, no setup needed, good warm-up
3. US1 (reducer) → highest coverage value, unblocks confidence in state transitions
4. US2 (selectors) → verifies data access contracts
5. US3 (effects) → most complex, build last with full context

### Note on Implementation Order vs. Priority Order

The spec priorities (P1→P2→P3→P4) drive the phase structure above. However, **for a single developer**, the recommended warm-up sequence from plan.md is: actions (US4) → reducer (US1) → selectors (US2) → effects (US3). This is noted here but does not change the phase numbering.

---

## Notes

- All 4 spec files write to different paths — phases 3–6 are fully parallelizable
- `chat.state.ts` has no spec (type definitions only)
- The `setTimeout` re-dispatch in `sendMessage$` is excluded from US3 test scope (see research.md R-006)
- `console.log` in `streamComplete` reducer: suppress in test `beforeEach` with `vi.spyOn(console, 'log').mockImplementation(() => {})`
- `uuidv4()` in `createConversation`: assert on the state structure, not on the specific UUID value
- Commit each spec file independently per constitution Phase 6 commit discipline
