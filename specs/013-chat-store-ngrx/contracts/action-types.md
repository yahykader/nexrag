# Contract: Chat Action Type Strings

**Feature**: 013-chat-store-ngrx  
**Source**: `agentic-rag-ui/src/app/features/chat/store/chat.actions.ts`

> These are the canonical action type strings. Tests MUST assert against these exact values.

## Conversation Actions

| Creator | Type String | Payload |
|---------|-------------|---------|
| `createConversation` | `[Chat] Create Conversation` | none |
| `loadConversations` | `[Chat] Load Conversations` | none |
| `loadConversationsSuccess` | `[Chat] Load Conversations Success` | `{ conversations: Conversation[] }` |
| `setActiveConversation` | `[Chat] Set Active Conversation` | `{ conversationId: string }` |
| `deleteConversation` | `[Chat] Delete Conversation` | `{ conversationId: string }` |

## Message Actions

| Creator | Type String | Payload |
|---------|-------------|---------|
| `sendMessage` | `[Chat] Send Message` | `{ content: string; conversationId?: string }` |
| `addUserMessage` | `[Chat] Add User Message` | `{ message: Message }` |
| `addAssistantMessage` | `[Chat] Add Assistant Message` | `{ messageId: string }` |

## Streaming Actions

| Creator | Type String | Payload |
|---------|-------------|---------|
| `streamConnected` | `[Chat] Stream Connected` | `{ event: StreamEvent }` |
| `streamToken` | `[Chat] Stream Token` | `{ event: StreamEvent }` |
| `streamComplete` | `[Chat] Stream Complete` | `{ event: StreamEvent }` |
| `streamError` | `[Chat] Stream Error` | `{ error: string }` |
| `cancelStream` | `[Chat] Cancel Stream` | none |
| `cancelStreamSuccess` | `[Chat] Cancel Stream Success` | none |

## Input / Error Actions

| Creator | Type String | Payload |
|---------|-------------|---------|
| `updateInputText` | `[Chat] Update Input Text` | `{ text: string }` |
| `clearInputText` | `[Chat] Clear Input Text` | none |
| `clearError` | `[Chat] Clear Error` | none |
