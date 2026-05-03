# Service Contract: ChatResolver

**Source**: `agentic-rag-ui/src/app/features/chat/resolvers/chat.resolver.ts`  
**Spec**: `chat.resolver.spec.ts` (co-located)  
**Phase**: 8

---

## Interface

```typescript
class ChatResolver implements Resolve<void> {
  resolve(route?: ActivatedRouteSnapshot, state?: RouterStateSnapshot): Observable<void>
}
```

## Behaviour Contract

| Precondition | Action | Observable result |
|---|---|---|
| `selectActiveConversationId` emits `null` | Dispatch `ChatActions.createConversation()` | Emits `void` once and completes |
| `selectActiveConversationId` emits a non-null string | No action dispatched | Emits `void` once and completes |

## Invariants

- `take(1)` guarantees the selector is consumed exactly once regardless of further emissions.
- The observable ALWAYS completes — it NEVER blocks the route indefinitely.
- The observable NEVER errors — failure modes of `createConversation` are handled by effects, not the resolver.

## Store Dependencies

| Read | Write (conditional) |
|---|---|
| `ChatSelectors.selectActiveConversationId` | `ChatActions.createConversation()` |

## Test File Structure

```
describe('ChatResolver') {
  // US1 — Resolver logic
  it('doit dispatcher createConversation quand aucune conversation active')
  it('ne doit pas dispatcher createConversation quand une conversation est déjà active')
  it('doit émettre une fois et compléter sans bloquer la navigation')
}
```

## Isolation Strategy for Tests

```typescript
const createService = createServiceFactory({
  service: ChatResolver,
  providers: [
    provideMockStore({ initialState: { chat: mockChatState([], null) } }),
  ],
});

// Per-test: override store state via spectator.inject(MockStore)
const mockStore = spectator.inject(MockStore);
mockStore.setState({ chat: mockChatState([], 'conv-1') });
```
