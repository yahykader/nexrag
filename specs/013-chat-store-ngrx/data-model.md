# Data Model: Chat Store NgRx (Phase 6)

**Feature**: 013-chat-store-ngrx  
**Date**: 2026-04-30  
**Source**: `agentic-rag-ui/src/app/features/chat/store/chat.state.ts`

> This model is derived directly from the source files. It supersedes any simplified model
> described in spec.md for implementation and test purposes.

---

## Entities

### Message

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | `string` | yes | UUID — unique per message |
| `role` | `'user' \| 'assistant' \| 'system'` | yes | Who authored the message |
| `content` | `string` | yes | Text content; assistant messages accumulate via `streamToken` events |
| `timestamp` | `Date` | yes | Creation time |
| `status` | `'pending' \| 'streaming' \| 'complete' \| 'error'` | yes | Lifecycle state |
| `citations` | `Citation[]` | no | RAG source references; populated on `streamComplete` |
| `metadata` | `{ model?, tokens?, sources? }` | no | LLM metadata; populated on `streamComplete` |

**Status transitions**: `pending → streaming → complete | error`

---

### Citation

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `index` | `number` | yes | 1-based citation index in response |
| `content` | `string` | yes | Source filename |
| `sourceFile` | `string` | no | Full file path |
| `sourcePage` | `number \| null` | no | Page number within file |

---

### Conversation

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | `string` | yes | UUID — EntityAdapter primary key |
| `title` | `string` | yes | Auto-set from first user message (first 50 chars) |
| `messages` | `Message[]` | yes | Ordered list, oldest first |
| `createdAt` | `Date` | yes | Conversation creation time |
| `updatedAt` | `Date` | yes | Last modified; EntityAdapter sorts descending by this field |

---

### ConversationsState

EntityAdapter state wrapping `Conversation[]`:

| Field | Type | Description |
|-------|------|-------------|
| `ids` | `string[]` | Ordered array of conversation IDs |
| `entities` | `{ [id: string]: Conversation }` | O(1) dictionary lookup |

---

### ChatState (full)

| Field | Type | Initial Value | Description |
|-------|------|---------------|-------------|
| `conversations` | `ConversationsState` | `adapter.getInitialState()` | All conversations via EntityAdapter |
| `activeConversationId` | `string \| null` | `null` | Currently selected conversation |
| `isStreaming` | `boolean` | `false` | True while assistant is generating |
| `streamingMessageId` | `string \| null` | `null` | ID of the assistant message receiving tokens |
| `streamSessionId` | `string \| null` | `null` | SSE session ID from `connected` event |
| `inputText` | `string` | `''` | Current value of the chat input field |
| `loading` | `boolean` | `false` | True while conversations are being loaded |
| `error` | `string \| null` | `null` | Last error message |

---

## State Transitions (Reducer)

### Conversation lifecycle

| Action | State change |
|--------|-------------|
| `createConversation` | Adds new `Conversation` via `adapter.addOne()`; sets `activeConversationId` |
| `loadConversationsSuccess` | **Replaces** all conversations via `adapter.setAll()` (confirmed Q1); sets `loading: false` |
| `setActiveConversation` | Updates `activeConversationId` |
| `deleteConversation` | Removes via `adapter.removeOne()`; clears `activeConversationId` if it matched |

### Message lifecycle

| Action | State change |
|--------|-------------|
| `addUserMessage` | Appends `message` to active conversation's `messages[]`; sets title from first message |
| `addAssistantMessage` | Appends blank assistant message (status: `streaming`); sets `isStreaming: true`, `streamingMessageId` |
| `streamConnected` | Sets `streamSessionId` from event |
| `streamToken` | Appends `event.text` to `streamingMessageId` message content; **guarded**: no-op if `streamingMessageId` is null (confirmed Q3) |
| `streamComplete` | Sets message `status: 'complete'`, populates `citations[]` from `event.response.sources`; sets `isStreaming: false`, clears `streamingMessageId` and `streamSessionId` |
| `streamError` | Sets `isStreaming: false`, clears `streamingMessageId`/`streamSessionId`, stores `error` string; **no retry** (confirmed Q4) |
| `cancelStreamSuccess` | Sets `isStreaming: false`, clears `streamingMessageId`/`streamSessionId` |

### Input / error lifecycle

| Action | State change |
|--------|-------------|
| `updateInputText` | Sets `inputText` |
| `clearInputText` | Sets `inputText: ''` |
| `clearError` | Sets `error: null` |

---

## AppState Shape (test fixture)

```typescript
// Used in selector tests
const mockAppState = {
  chat: {
    conversations: conversationsAdapter.getInitialState(),
    activeConversationId: null,
    isStreaming: false,
    streamingMessageId: null,
    streamSessionId: null,
    inputText: '',
    loading: false,
    error: null
  }
};
```

---

## StreamEvent Contract

Source: `agentic-rag-ui/src/app/core/services/streaming-api.service.ts`

```typescript
interface StreamEvent {
  type: 'connected' | 'token' | 'complete' | 'error';
  sessionId?: string;      // present on 'connected'
  conversationId?: string; // present on 'connected'
  text?: string;           // present on 'token' (citations already stripped)
  index?: number;          // present on 'token'
  response?: any;          // present on 'complete' (contains sources[])
  metadata?: any;          // present on 'complete'
  error?: string;          // present on 'error'
  code?: string;           // present on 'error'
}
```
