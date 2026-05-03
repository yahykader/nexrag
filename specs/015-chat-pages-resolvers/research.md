# Research: Phase 8 — Chat Pages & Resolvers Test Coverage

**Feature**: `015-chat-pages-resolvers`  
**Generated**: 2026-05-04  
**Sources**: codebase exploration (`chat-page.component.ts`, `chat.resolver.ts`, `chat.state.ts`, `chat.selectors.ts`, `chat.actions.ts`), constitution v1.1.0, Phase 7 plan reference

---

## Decision 1: ConfirmationService Design

**Decision**: Create a minimal `@Injectable({ providedIn: 'root' })` class `ConfirmationService` in `src/app/core/services/` with a single `confirm(message: string): boolean` method that delegates to `window.confirm(message)`.

**Rationale**: The constitution requires all dependencies to be injectable (Principle VII — DIP). `window.confirm` is a DOM global that Vitest's JSDOM returns `false` for unconditionally, making the confirm/cancel gate untestable without extraction. Wrapping it in a service allows `mockProvider(ConfirmationService, { confirm: () => true })` in specs.

**Alternatives considered**:
- `vi.spyOn(window, 'confirm')` — rejected (Clarification Q1) due to JSDOM limitations and because it leaks across tests.
- Skip the confirm/cancel gate — rejected; FR-010 requires both positive and negative paths.

---

## Decision 2: ChatInterfaceComponent Stubbing

**Decision**: In `chat-page.component.spec.ts`, declare an inline stub:
```typescript
@Component({ selector: 'app-chat-interface', template: '', standalone: true })
class ChatInterfaceStub {}
```
Then pass it in the `overrideComponent` call or replace the real import in the `imports` array of `createComponentFactory`.

**Rationale**: Explicit stub preserves selector-match verification (US2.5: the element `app-chat-interface` must be present in the DOM), whereas `NO_ERRORS_SCHEMA` silently ignores selector mismatches. Confirmed by Clarification Q2.

**Alternatives considered**:
- `NO_ERRORS_SCHEMA` — rejected (Clarification Q2); hides selector regressions.
- Real `ChatInterfaceComponent` in all tests — rejected; requires the entire chat store and service tree, violating test isolation (Constitution Principle VI).

---

## Decision 3: `provideMockStore` InitialState Shape for EntityAdapter

**Decision**: Build the `ConversationsState` using `conversationsAdapter.getInitialState(...)` from `chat.state.ts` to produce a correctly shaped EntityState (`ids: string[], entities: Record<string, Conversation>`). Example factory for tests:

```typescript
import { conversationsAdapter, initialChatState } from '../store/chat.state';

export function mockChatState(conversations: Conversation[], activeId: string | null = null) {
  return {
    ...initialChatState,
    conversations: conversationsAdapter.setAll(conversations, conversationsAdapter.getInitialState()),
    activeConversationId: activeId,
  };
}
```

**Rationale**: The `selectConversations` selector uses `conversationsAdapter.getSelectors()` which expects the `ids`/`entities` shape of an `EntityState`. Passing a plain array directly as `conversations` would break the selector. Using `conversationsAdapter.setAll()` is the correct, adapter-provided way to seed test state.

**Alternatives considered**:
- Manually constructing `{ ids: [...], entities: {...} }` inline — works but fragile; any adapter change would silently break all tests.
- Using `conversationsAdapter.addMany()` — equivalent, but `setAll` is cleaner for test initialization.

---

## Decision 4: Testing `Observable<void>` Completion

**Decision**: Use a direct subscription with `next`/`complete` callbacks (no `TestScheduler` or `firstValueFrom`):

```typescript
it('doit émettre une fois et compléter', () => {
  let emitCount = 0;
  let completed = false;
  spectator.service.resolve({} as any, {} as any).subscribe({
    next: () => emitCount++,
    complete: () => { completed = true; },
  });
  expect(emitCount).toBe(1);
  expect(completed).toBe(true);
});
```

**Rationale**: The resolver uses `of(void 0)` via `switchMap`, which is synchronous. No async scheduling is needed. A subscription callback approach is the simplest, most readable assertion for synchronous observable completion. Confirmed by Clarification Q5.

**Alternatives considered**:
- `firstValueFrom()` — only asserts the emitted value, not completion.
- `TestScheduler` — overkill for synchronous observables; adds unnecessary complexity.
- `lastValueFrom()` — valid alternative; subscription callback chosen for symmetry with how the resolver's two scenarios (dispatch + no-dispatch) are tested side-by-side.

---

## Decision 5: Integration Test Scope

**Decision**: The 2 integration tests use the real `ChatPageComponent` template (not shallow) with `ChatInterfaceComponent` replaced by a stub, but with a real `MockStore` populated with known state:
1. `[INTÉGRATION] doit inclure app-chat-interface dans le template principal` — mounts full page, asserts element presence.
2. `[INTÉGRATION] dispatch complet : sidebar click → store action` — simulates a full user click flow (select conversation → assert dispatched action).

**Rationale**: Integration tests exercise the page + template + store binding together. Using a stub for `ChatInterfaceComponent` keeps the test scope bounded to the page itself while exercising the real template rendering logic.

**Alternatives considered**:
- Full tree with real `ChatInterfaceComponent` — rejected; imports the entire chat store slice and service dependencies, making the test slow and brittle.
- Store dispatch verification only (no DOM) — rejected; that is already covered by unit tests (US3); integration tests must exercise the full component tree.

---

## Decision 6: `onDestroy` Scope

**Decision**: Out of scope for Phase 8. All store bindings in `ChatPageComponent` use the `async` pipe, which self-manages teardown. No `ngOnDestroy` implementation is needed.

**Rationale**: Constitution Principle VI specifies "default to shallow rendering unless the test explicitly verifies child-component interaction." Since all subscriptions are via `async` pipe, there is nothing to test for teardown in the current implementation. Confirmed by Clarification Q3.

---

## Summary of `ChatPageComponent` Selector Bindings (from codebase)

| Property | Selector | NgRx Source |
|---|---|---|
| `conversations$` | `selectConversations` | `conversationsAdapter.getSelectors` → `selectAll` |
| `activeConversationId$` | `selectActiveConversationId` | `state.activeConversationId` |

## Summary of `ChatResolver` Logic (from codebase)

```
resolve() {
  store.select(selectActiveConversationId)   // reads current active ID
    .pipe(
      take(1),                               // only first emission
      tap(activeId => {
        if (!activeId) store.dispatch(createConversation())  // guard
      }),
      switchMap(() => of(void 0))            // always completes with void
    )
}
```

Key test implications:
- When `activeId = null` → `createConversation` dispatched; observable still completes.
- When `activeId = 'some-id'` → nothing dispatched; observable completes immediately.
- In both cases: `take(1)` guarantees the selector is consumed exactly once.
