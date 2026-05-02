# Implementation Plan: Chat Store NgRx Tests (Phase 6)

**Branch**: `013-chat-store-ngrx` | **Date**: 2026-04-30 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `specs/013-chat-store-ngrx/spec.md`

---

## Summary

Create 4 spec files co-located with the 5 chat store source files to achieve ≥80% statement / ≥85% function coverage for the NgRx chat slice. The store manages multi-conversation state via `@ngrx/entity` EntityAdapter, SSE-based streaming via `StreamingApiService`, and a two-effect pipeline (`sendMessage$` → `startStreaming$`). All tests are pure unit tests — no Angular bootstrapping, no real HTTP, no real WebSocket.

---

## Technical Context

**Language/Version**: TypeScript 5.9 / Angular 21  
**Primary Dependencies**: `@ngrx/store ^21.0.1`, `@ngrx/effects ^21.0.1`, `@ngrx/entity ^21.0.1`, `@ngneat/spectator ^22.1.0`, `@ngrx/store/testing ^21.0.1`, `@ngrx/effects/testing ^21.0.1`  
**Storage**: N/A — no persistence in unit tests  
**Testing**: Vitest ^4.0.8 (replaces Karma/Jasmine per constitution)  
**Target Platform**: Node.js / JSDOM (Vitest test environment)  
**Project Type**: Frontend SPA feature — NgRx store layer unit tests  
**Performance Goals**: Each spec file runs in under 2 seconds; full Phase 6 suite in under 10 seconds  
**Constraints**: No real `EventSource`, no real `Store`, no real service calls; all external dependencies mocked  
**Scale/Scope**: 4 spec files, ~22 `it()` blocks, 0 integration tests

---

## Constitution Check

*GATE: Must pass before implementation begins.*

### Principle VI — Angular Component Test Isolation ✅

- Reducer tests: pure function calls, no TestBed needed. ✅
- Selector tests: direct projection calls with mock state. ✅
- Effects tests: `createServiceFactory` from Spectator with `provideMockActions` + `provideMockStore`. ✅
- Action tests: direct `createAction` call assertions. ✅
- No `StoreModule.forRoot()` in any test. ✅

### Principle VII — SOLID Reflected in Tests ✅

- One spec file per source file: `chat.reducer.spec.ts`, `chat.selectors.spec.ts`, `chat.effects.spec.ts`, `chat.actions.spec.ts`. ✅
- `chat.state.ts` is type-only; no spec needed. ✅

### Principle VIII — Naming Conventions ✅

- `describe('ChatReducer', ...)` / `describe('ChatSelectors', ...)` etc. — English class names. ✅
- `it('doit retourner l\'état initial', ...)` — French imperative. ✅
- Co-located: each `*.spec.ts` beside its `*.ts` in `features/chat/store/`. ✅

### Principle IX — Coverage Gates ✅

- Target: statements ≥80%, branches ≥75%, functions ≥85%, lines ≥80%. ✅
- CI blocks merge if any gate fails. ✅

### Principle X — NgRx Testing Contracts ✅

- Reducers: pure function `chatReducer(state, action)`. ✅
- Selectors: mock `AppState` object. ✅
- Effects: `provideMockActions` + `provideMockStore` — confirmed by constitution. ✅
- No real `EventSource` in unit tests; `StreamingApiService` mocked as `SpyObject<StreamingApiService>`. ✅

**GATE RESULT: PASS** — no violations. No Complexity Tracking entry needed.

---

## Project Structure

### Documentation (this feature)

```text
specs/013-chat-store-ngrx/
├── plan.md              ← this file
├── research.md          ← Phase 0 complete
├── data-model.md        ← Phase 1 complete
├── quickstart.md        ← Phase 1 complete
├── contracts/
│   ├── action-types.md  ← Phase 1 complete
│   └── selectors.md     ← Phase 1 complete
├── checklists/
│   └── requirements.md  ← quality checklist (all pass)
└── tasks.md             ← Phase 2 output (/speckit.tasks — not yet created)
```

### Source Code (Angular frontend)

```text
agentic-rag-ui/src/app/features/chat/store/
├── chat.state.ts              — entities: Message, Citation, Conversation, ChatState
├── chat.actions.ts            — 17 action creators
├── chat.actions.spec.ts       ← CREATE: 4 action type + payload tests
├── chat.reducer.ts            — chatReducer (pure function, 15 on() handlers)
├── chat.reducer.spec.ts       ← CREATE: ~10 reducer state transition tests
├── chat.selectors.ts          — 10 selectors (EntityAdapter + custom)
├── chat.selectors.spec.ts     ← CREATE: ~6 selector projection tests
├── chat.effects.ts            — 3 effects: sendMessage$, startStreaming$, cancelStream$
└── chat.effects.spec.ts       ← CREATE: ~8 effect behavior tests
```

---

## Implementation Blueprint

### Spec 1: `chat.actions.spec.ts` (Priority: P4 — lowest risk, good warm-up)

**Goal**: Verify every action creator produces the correct type string and payload shape.

```typescript
describe('ChatActions', () => {
  it('sendMessage doit avoir le type [Chat] Send Message et le payload content', () => { ... });
  it('addUserMessage doit inclure le message complet dans le payload', () => { ... });
  it('streamToken doit encapsuler le StreamEvent dans event', () => { ... });
  it('streamError doit avoir error dans le payload', () => { ... });
  it('loadConversationsSuccess doit inclure le tableau conversations', () => { ... });
});
```

**Key assertions**: `action.type === '[Chat] Send Message'`, `action.content === '...'`

---

### Spec 2: `chat.reducer.spec.ts` (Priority: P1 — highest risk)

**Goal**: Verify all 15 `on()` handlers produce the correct next state.

**Setup**:
```typescript
// Helper — creates state with one active conversation
function stateWithActiveConversation(): ChatState { ... }
```

**Test list**:
```typescript
describe('ChatReducer', () => {
  it('doit retourner l\'état initial', () => { ... });

  // Conversations
  it('createConversation doit ajouter une conversation et la définir comme active', () => { ... });
  it('loadConversationsSuccess doit remplacer toutes les conversations (setAll)', () => { ... });
  it('loadConversationsSuccess doit remplacer même si des messages existent déjà', () => { ... }); // Q1
  it('deleteConversation doit retirer la conversation et effacer activeConversationId si elle était active', () => { ... });

  // Messages
  it('addUserMessage doit ajouter le message à la conversation active', () => { ... });
  it('addUserMessage doit définir le titre depuis le premier message', () => { ... });
  it('addAssistantMessage doit ajouter un message vide et passer isStreaming à true', () => { ... });

  // Streaming
  it('streamToken doit accumuler le texte dans le message en cours', () => { ... });
  it('streamToken doit être ignoré si streamingMessageId est null', () => { ... }); // Q3
  it('streamComplete doit marquer le message complete et extraire les citations', () => { ... });
  it('streamComplete doit passer isStreaming à false', () => { ... });
  it('streamError doit passer isStreaming à false et stocker l\'erreur', () => { ... });

  // Input / Error
  it('clearInputText doit vider inputText', () => { ... });
  it('clearError doit mettre error à null', () => { ... });
});
```

---

### Spec 3: `chat.selectors.spec.ts` (Priority: P2)

**Goal**: Verify each selector returns the correct projected value from mock state.

**Setup**:
```typescript
function buildState(overrides: Partial<ChatState> = {}): AppState {
  return { chat: { ...initialChatState, ...overrides } };
}

function stateWithConversation(conv: Partial<Conversation>): AppState {
  const c: Conversation = { id: 'c1', title: 'test', messages: [], createdAt: new Date(), updatedAt: new Date(), ...conv };
  return buildState({
    conversations: conversationsAdapter.addOne(c, conversationsAdapter.getInitialState()),
    activeConversationId: c.id
  });
}
```

**Test list**:
```typescript
describe('ChatSelectors', () => {
  it('selectConversations doit retourner un tableau vide par défaut', () => { ... });
  it('selectActiveConversationId doit retourner null par défaut', () => { ... });
  it('selectActiveMessages doit retourner [] si pas de conversation active', () => { ... });
  it('selectActiveMessages doit retourner les messages de la conversation active', () => { ... });
  it('selectIsStreaming doit retourner false par défaut', () => { ... });
  it('selectError doit retourner null par défaut', () => { ... });
  it('selectRecentConversations doit retourner au maximum 5 conversations', () => { ... });
});
```

---

### Spec 4: `chat.effects.spec.ts` (Priority: P3)

**Goal**: Verify the 3 effects dispatch the correct action sequences.

**Setup**:
```typescript
let actions$: ReplaySubject<Action>;

const createService = createServiceFactory({
  service: ChatEffects,
  providers: [
    provideMockActions(() => actions$),
    provideMockStore({
      initialState: { chat: initialChatState }
    })
  ],
  mocks: [StreamingApiService]
});
```

**Test list**:
```typescript
describe('ChatEffects', () => {
  describe('sendMessage$', () => {
    it('doit dispatcher createConversation si aucune conversation n\'est active', () => { ... });
    it('doit dispatcher addUserMessage, addAssistantMessage, clearInputText si une conv est active', () => { ... });
  });

  describe('startStreaming$', () => {
    it('doit appeler streamingApi.stream() avec la query et le conversationId', () => { ... });
    it('doit dispatcher streamConnected pour l\'événement connected', () => { ... });
    it('doit dispatcher streamToken pour chaque token reçu', () => { ... });
    it('doit dispatcher streamComplete pour l\'événement complete', () => { ... });
    it('doit dispatcher streamError si le service échoue (catchError)', () => { ... });
    it('doit dispatcher streamError si aucun message utilisateur n\'est trouvé', () => { ... });
  });

  describe('cancelStream$', () => {
    it('doit dispatcher cancelStreamSuccess directement si streamSessionId est null', () => { ... });
  });
});
```

**Note on setTimeout in sendMessage$**: The re-dispatch via `setTimeout` in the no-active-conversation branch is excluded from unit test scope (see research.md R-006). The test verifies only that `createConversation` is dispatched.

---

## Implementation Order

Follow constitution Principle X ordering (test-first TDD flow):

1. `chat.actions.spec.ts` — pure type assertions, zero setup, good warm-up
2. `chat.reducer.spec.ts` — pure function, no DI, highest coverage value
3. `chat.selectors.spec.ts` — pure projection, mock state only
4. `chat.effects.spec.ts` — most complex setup; build on knowledge from steps 1-3

Each spec file is committed independently per constitution commit discipline.

---

## Known Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| `setTimeout` in `sendMessage$` untestable | Low — only 1 branch | Document exclusion; test `createConversation` dispatch only (R-006) |
| EntityAdapter state shape not matching mock | Medium — test setup | Use `conversationsAdapter.getInitialState()` always; never hand-build `ids/entities` |
| `console.log` in reducer pollutes test output | Low | Suppress via `vi.spyOn(console, 'log').mockImplementation(() => {})` in `beforeEach` |
| `uuidv4()` non-deterministic in reducer | Medium | Capture generated IDs by inspecting the returned state, not predicting them |
| `withLatestFrom` in effects requires store state | Medium | Use `MockStore.setState()` or `overrideSelector()` to set active conversation |
