# Research: Chat Store NgRx Tests (Phase 6)

**Feature**: 013-chat-store-ngrx  
**Date**: 2026-04-30  
**Status**: Complete — all NEEDS CLARIFICATION resolved

---

## R-001: Effects Testing Strategy

**Decision**: Use `provideMockActions` (ReplaySubject-backed) + `provideMockStore` + `SpyObject<StreamingApiService>` from Spectator. Observable results are simulated with `of()` and `throwError()`. No marble testing.

**Rationale**: Marble syntax adds cognitive overhead for the 5 effect scenarios covered here. Standard `of()` / `throwError()` factories produce deterministic synchronous sequences sufficient for unit verification. Marble testing is deferred to integration phases where timing precision matters.

**Alternatives considered**:
- Hot/cold marble syntax (`TestScheduler`) — rejected: increases complexity without coverage benefit for these specific effect paths.
- Real `Actions` from `@ngrx/effects` — rejected: requires bootstrapping a full Angular test bed and breaks isolation.

---

## R-002: Reducer Testing Strategy

**Decision**: Test reducers as pure functions by calling `chatReducer(state, action)` directly. No `TestBed`, no `StoreModule`.

**Rationale**: NgRx reducers are pure functions — they take state + action and return new state. Constitution Principle X mandates this approach. No injection or module setup is needed.

**Alternatives considered**:
- Using `provideMockStore` for reducer tests — rejected: adds unnecessary DI overhead; reducers are not injected.

---

## R-003: Selector Testing Strategy

**Decision**: Build a typed `mockAppState` object (`{ chat: ChatState }`) using `conversationsAdapter.getInitialState()` and spread overrides. Assert on selector projector output directly.

**Rationale**: `createFeatureSelector` uses the slice key `'chat'` — tests must provide a full `{ chat: ChatState }` object. Using the adapter's `getInitialState()` ensures the `ids[]` / `entities{}` shape matches the EntityAdapter contract.

**Alternatives considered**:
- Calling selectors on a real `Store` — rejected: requires module setup; overkill for pure projection functions.

---

## R-004: Multi-Conversation Architecture (Spec delta)

**Decision**: The spec assumed a single active conversation (clarification Q5: A). The actual implementation uses `@ngrx/entity` `EntityAdapter<Conversation>` with an `activeConversationId` pointer. Tests must account for this.

**Impact on spec**: The spec's `ChatMessage` entity must be updated to reflect the real `Message` interface (includes `status`, `citations`, `metadata`). The `ChatState` entity must include `conversations: EntityState<Conversation>`, `activeConversationId`, `streamingMessageId`, `streamSessionId`, `inputText`, and `loading`.

**Rationale**: Reading the actual source files reveals the real shape. The spec was written before reading source code; the data-model.md below is authoritative.

---

## R-005: Two-Effect Streaming Architecture

**Decision**: Streaming is split across two effects:
1. `sendMessage$` — dispatches `addUserMessage`, `addAssistantMessage`, `clearInputText`
2. `startStreaming$` — triggered by `addAssistantMessage`; calls `streamingApi.stream()`

**Impact on tests**: `sendMessage$` test does NOT test SSE streaming; `startStreaming$` test is the SSE integration point. Tests must set up store state with an active conversation containing user messages for `startStreaming$` to function.

**Rationale**: The `sendMessage$` effect uses `switchMap` (cancel-and-restart, confirmed in Q2) but delegates actual streaming to `startStreaming$` — a clean separation of UI message creation from I/O concerns.

---

## R-006: setTimeout Anti-Pattern in sendMessage$

**Decision**: The no-active-conversation branch of `sendMessage$` dispatches `createConversation` then uses `setTimeout(() => store.dispatch(sendMessage(...)), 100)` — an untestable imperative side effect.

**Impact on tests**: This branch CANNOT be fully tested with pure effect testing. The test for this branch should verify only that `createConversation` is dispatched (and a `NO_OP` is returned); the re-dispatch via `setTimeout` is excluded from unit test scope.

**Rationale**: Testing `setTimeout`-based `store.dispatch` calls requires `fakeAsync + tick(100)` inside an effect test, which is not compatible with `provideMockActions`. This is a known anti-pattern; it is flagged here as a testing limitation and a candidate for refactoring.

---

## R-007: StreamEvent Contract

**Decision**: `StreamingApiService.stream()` returns `Observable<StreamEvent>` where:
```
StreamEvent.type: 'connected' | 'token' | 'complete' | 'error'
StreamEvent.text?: string          // populated for 'token'
StreamEvent.sessionId?: string     // populated for 'connected'
StreamEvent.response?: any         // populated for 'complete' (contains sources[])
StreamEvent.metadata?: any         // populated for 'complete'
StreamEvent.error?: string         // populated for 'error'
StreamEvent.code?: string          // populated for 'error'
```

**Impact on tests**: Mock the service to return `of(connectedEvent, tokenEvent, completeEvent)` for the happy path. For error path: `of(errorEvent)` or `throwError(() => new Error('..'))`.

---

## R-008: Entity Adapter Selectors

**Decision**: `chat.selectors.ts` exposes adapter-generated selectors via `conversationsAdapter.getSelectors()`. The exported selectors are:
- `selectConversations` — all conversations as array
- `selectConversationsDictionary` — `{ [id]: Conversation }` map
- `selectActiveConversationId`, `selectActiveConversation`, `selectActiveMessages`
- `selectIsStreaming`, `selectInputText`, `selectError`
- `selectConversationById(id)` — factory (parameterized)
- `selectRecentConversations` — last 5 by `updatedAt` sort

**Impact on tests**: The `selectActiveMessages` selector returns `conversation?.messages || []` — test both the undefined (no active conversation) and populated cases.
