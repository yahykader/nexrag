# Contract: Chat Selectors

**Feature**: 013-chat-store-ngrx  
**Source**: `agentic-rag-ui/src/app/features/chat/store/chat.selectors.ts`

> All selectors project from `AppState = { chat: ChatState }`.
> Tests must build a mock `AppState` and pass it directly to the selector.

## Feature Selector

| Selector | Returns | Notes |
|----------|---------|-------|
| `selectChatState` | `ChatState` | `createFeatureSelector('chat')` — slice key is `'chat'` |

## Conversation Selectors (EntityAdapter-generated)

| Selector | Returns | Notes |
|----------|---------|-------|
| `selectConversations` | `Conversation[]` | All conversations, sorted descending by `updatedAt` |
| `selectConversationsDictionary` | `{ [id: string]: Conversation \| undefined }` | O(1) lookup map |
| `selectActiveConversationId` | `string \| null` | Current active conversation ID |
| `selectActiveConversation` | `Conversation \| undefined` | Derived from dictionary + active ID |
| `selectActiveMessages` | `Message[]` | `conversation?.messages \|\| []` — empty array when no active conversation |
| `selectConversationById(id)` | `Conversation \| undefined` | Parameterized factory selector |
| `selectRecentConversations` | `Conversation[]` | First 5 from `selectConversations` |

## UI / Streaming Selectors

| Selector | Returns | Notes |
|----------|---------|-------|
| `selectIsStreaming` | `boolean` | `state.isStreaming` |
| `selectInputText` | `string` | `state.inputText` |
| `selectError` | `string \| null` | `state.error` |

## Test Fixture Pattern

```typescript
import { conversationsAdapter } from './chat.state';

function buildState(overrides: Partial<ChatState> = {}): { chat: ChatState } {
  return {
    chat: {
      conversations: conversationsAdapter.getInitialState(),
      activeConversationId: null,
      isStreaming: false,
      streamingMessageId: null,
      streamSessionId: null,
      inputText: '',
      loading: false,
      error: null,
      ...overrides
    }
  };
}
```
