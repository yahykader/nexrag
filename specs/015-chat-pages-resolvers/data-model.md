# Data Model: Phase 8 — Chat Pages & Resolvers Test Coverage

**Feature**: `015-chat-pages-resolvers`  
**Generated**: 2026-05-04

---

## Production Entities (existing — read-only for this phase)

### `Conversation`
```typescript
interface Conversation {
  id: string;             // UUID — entity identity key (used by EntityAdapter)
  title: string;          // Display name shown in sidebar
  messages: Message[];    // Ordered list of messages in this conversation
  createdAt: Date;        // ISO timestamp — creation
  updatedAt: Date;        // ISO timestamp — last modified; adapter sorts descending by this
}
```
**Notes**: Managed by `conversationsAdapter` (NgRx EntityAdapter). Sorted by `updatedAt` descending. The active conversation is tracked separately via `activeConversationId` (not an attribute of `Conversation`).

### `ChatState`
```typescript
interface ChatState {
  conversations: ConversationsState;    // EntityState<Conversation> — ids[] + entities{}
  activeConversationId: string | null;  // ID of the currently selected conversation
  isStreaming: boolean;
  streamingMessageId: string | null;
  streamSessionId: string | null;
  inputText: string;
  loading: boolean;
  error: string | null;
}
```
**Key for tests**: `provideMockStore` initialState MUST use `conversationsAdapter.setAll(conversations, conversationsAdapter.getInitialState())` to populate `conversations` — never a plain array.

---

## New Production Entity (to create in this phase)

### `ConfirmationService`
```typescript
@Injectable({ providedIn: 'root' })
class ConfirmationService {
  confirm(message: string): boolean   // delegates to window.confirm(message)
}
```
**Location**: `src/app/core/services/confirmation.service.ts`  
**Purpose**: Wraps `window.confirm()` to enable DI-based mocking in tests (see Research Decision 1).  
**Test mock**: `mockProvider(ConfirmationService, { confirm: vi.fn().mockReturnValue(true) })`

---

## Test Data Factories (to define in spec files)

### `mockConversation(overrides?)`
```typescript
function mockConversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    id: 'conv-1',
    title: 'Test Conversation',
    messages: [],
    createdAt: new Date('2026-01-01'),
    updatedAt: new Date('2026-01-01'),
    ...overrides,
  };
}
```

### `mockChatState(conversations, activeId?)`
```typescript
function mockChatState(
  conversations: Conversation[],
  activeId: string | null = null
): ChatState {
  return {
    ...initialChatState,
    conversations: conversationsAdapter.setAll(
      conversations,
      conversationsAdapter.getInitialState()
    ),
    activeConversationId: activeId,
  };
}
```

---

## NgRx Selectors Used in Phase 8 Tests

| Selector | Returns | Used in |
|---|---|---|
| `selectConversations` | `Conversation[]` | `ChatPageComponent.conversations$` |
| `selectActiveConversationId` | `string \| null` | `ChatPageComponent.activeConversationId$` + `ChatResolver.resolve()` |

---

## NgRx Actions Dispatched in Phase 8

| Action | Trigger | Tested in |
|---|---|---|
| `createConversation()` | "Nouveau" button click / resolver (no active) | `chat-page.component.spec.ts`, `chat.resolver.spec.ts` |
| `setActiveConversation({ conversationId })` | Conversation item click | `chat-page.component.spec.ts` |
| `deleteConversation({ conversationId })` | Delete button + confirmed | `chat-page.component.spec.ts` |

---

## State Transitions Verified by Tests

```
Initial render (no conversations):
  conversations = []  →  empty-state indicator visible

Initial render (2 conversations, activeId = 'conv-1'):
  conversations = [conv-1, conv-2]  →  2 sidebar items; conv-1 has 'active' class

Resolver (activeConversationId = null):
  dispatch createConversation()  →  observable completes with void

Resolver (activeConversationId = 'conv-1'):
  no dispatch  →  observable completes with void

Sidebar toggle:
  sidebarCollapsed=false  →  toggleSidebar()  →  sidebarCollapsed=true + 'collapsed' class
```
