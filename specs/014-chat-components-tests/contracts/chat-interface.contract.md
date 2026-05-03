# Component Contract: ChatInterfaceComponent

**Selector**: `app-chat-interface`  
**File**: `src/app/features/chat/components/chat-interface/chat-interface.component.ts`  
**Spec file to create**: `chat-interface.component.spec.ts` (co-located)

## Public Surface

### Inputs / Outputs
None — component is store-connected only. All state flows through NgRx selectors.

### Store Dependencies

| Selector | Type | Role in template |
|----------|------|-----------------|
| `selectActiveMessages` | `Observable<Message[]>` | `*ngFor` over message list |
| `selectIsStreaming` | `boolean` (subscribed) | Passed as `[isStreaming]` to MessageInputComponent |
| `selectInputText` | `string \| null` (subscribed) | Passed as `[inputText]` to MessageInputComponent |
| `selectError` | `Observable<string \| null>` | Error banner |
| `selectActiveConversationId` | `string \| null` (subscribed) | Triggers `createConversation()` if null |

### Dispatched Actions

| Action | When |
|--------|------|
| `ChatActions.createConversation()` | `ngOnInit` when `activeConversationId === null` |
| `ChatActions.sendMessage({ content })` | User submits via `(send)` output of MessageInputComponent |
| `ChatActions.cancelStream()` | User clicks cancel via `(cancel)` output |
| `ChatActions.updateInputText({ text })` | On `(inputChange)` from MessageInputComponent |

### Lifecycle Hooks

| Hook | Behaviour |
|------|-----------|
| `ngOnInit` | Subscribes to `selectActiveConversationId`; dispatches `createConversation` if null |
| `ngAfterViewChecked` | Calls `scrollToBottom()` when `shouldScrollToBottom === true` |
| `ngOnDestroy` | Calls `destroy$.next()` and `destroy$.complete()` |

## Test Strategy

### Setup
```typescript
const createComponent = createComponentFactory({
  component: ChatInterfaceComponent,
  overrideModules: [],               // standalone component
  providers: [
    provideMockStore({
      initialState: {
        chat: {
          activeConversationId: 'conv-1',  // prevents createConversation dispatch
          isStreaming: false,
          inputText: '',
          error: null,
          conversations: { ids: ['conv-1'], entities: { 'conv-1': { id: 'conv-1', messages: [] } } }
        }
      }
    })
  ],
  schemas: [NO_ERRORS_SCHEMA],      // stubs MessageItemComponent and MessageInputComponent
});
```

### Unit Tests (6)
1. Smoke: component creates successfully
2. Messages rendered: mock store with 3 messages → 3 `app-message-item` elements in DOM
3. Empty state: mock store with 0 messages → no `app-message-item`, no crash
4. Streaming spinner: `isStreaming: true` in store → streaming indicator visible
5. MessageInput present: `app-message-input` is in the template
6. Init dispatch suppressed: `activeConversationId: 'conv-1'` → `createConversation` NOT dispatched

### Integration Tests (1 — prefixed `[INTÉGRATION]`)
7. Send message dispatch: trigger `(send)` output from `app-message-input` stub → `ChatActions.sendMessage` dispatched to store

## Naming Convention (Constitution Principle VIII)

```typescript
describe('ChatInterfaceComponent', () => {
  it('doit créer le composant', ...)
  it('doit afficher 3 messages quand le store en contient 3', ...)
  it('doit afficher un indicateur de streaming quand isStreaming=true', ...)
  it('[INTÉGRATION] doit dispatcher SendMessage quand l\'utilisateur soumet', ...)
})
```
