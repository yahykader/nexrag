# Data Model: Phase 7 — Chat Components Test Suite

**Feature**: `014-chat-components-tests`  
**Date**: 2026-05-02  
**Source**: Direct inspection of component `.ts` and `.html` files

---

## Core Domain Types

### Message (from `chat.state.ts`)

```typescript
interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date | string;   // rendered via | date:'HH:mm'
  status: 'pending' | 'streaming' | 'complete' | 'error';
  citations?: Citation[];
  metadata?: { tokens?: number };
}
```

### Citation (from `chat.state.ts`)

```typescript
interface Citation {
  index: number;
  content: string;           // may be markdown link format
  sourceFile?: string | null;
  sourcePage?: number | null;
}
```

### ChatState (NgRx slice)

```typescript
interface ChatState {
  conversations: EntityState<Conversation>; // @ngrx/entity
  activeConversationId: string | null;
  isStreaming: boolean;
  streamingMessageId: string | null;
  streamSessionId: string | null;
  inputText: string;
  error: string | null;
}
```

### Selectors used by `ChatInterfaceComponent`

| Selector | Returns | Notes |
|----------|---------|-------|
| `selectActiveMessages` | `Message[]` | Messages of the active conversation |
| `selectIsStreaming` | `boolean` | Whether LLM stream is active |
| `selectInputText` | `string \| null` | Current text field value |
| `selectError` | `string \| null` | Last error message |
| `selectActiveConversationId` | `string \| null` | Triggers `createConversation` if null |

---

## Component Input/Output Contracts

### ChatInterfaceComponent

| Kind | Name | Type | Notes |
|------|------|------|-------|
| — | — | — | No @Input() or @Output() — consumes store directly |
| ViewChild | `messagesContainer` | `ElementRef` | Used for `scrollTop` auto-scroll |
| Store dispatch | `sendMessage({ content })` | `ChatAction` | On `(send)` from MessageInputComponent |
| Store dispatch | `cancelStream()` | `ChatAction` | On `(cancel)` from MessageInputComponent |
| Store dispatch | `updateInputText({ text })` | `ChatAction` | On `(inputChange)` from MessageInputComponent |
| Store dispatch | `createConversation()` | `ChatAction` | On init when `activeConversationId === null` |

**Direct child elements in template**:
- `app-message-item` (repeated via `*ngFor` over `messages$ | async`)
- `app-message-input`

---

### MessageInputComponent

| Kind | Name | Type | Default | Notes |
|------|------|------|---------|-------|
| @Input | `inputText` | `string` | `''` | Bound textarea value |
| @Input | `disabled` | `boolean` | `false` | Disables input and send button |
| @Input | `isStreaming` | `boolean` | `false` | Shows cancel button when true |
| @Output | `send` | `EventEmitter<string>` | — | Emits trimmed content |
| @Output | `cancel` | `EventEmitter<void>` | — | Emits on cancel click |
| @Output | `inputChange` | `EventEmitter<string>` | — | Emits on textarea change |
| @Output | `transcript` | `EventEmitter<string>` | — | Voice transcript appended to inputText |
| @Output | `recordingChange` | `EventEmitter<boolean>` | — | True when mic is active |
| @Output | `voiceError` | `EventEmitter<string>` | — | Error from VoiceButtonComponent |

**Keyboard behaviour**: `Ctrl+Enter` (or `Cmd+Enter`) calls `onSend()`. Plain `Enter` has no special handling.

**Submit guard**: `onSend()` only emits if `inputText.trim()` is non-empty AND `!disabled`.

---

### MessageItemComponent

| Kind | Name | Type | Default | Notes |
|------|------|------|---------|-------|
| @Input | `message` | `Message` | required | Full message object |
| @Input | `isStreaming` | `boolean` | `false` | Shows `▊` cursor when `status==='streaming'` |

**CSS classes applied to host `div.message-item`**:
- `user-message` — when `message.role === 'user'`
- `assistant-message` — when `message.role === 'assistant'`
- `streaming` — when `isStreaming && message.status === 'streaming'`

**Timestamp rendering**: `{{ message.timestamp | date:'HH:mm' }}` — absolute 24-hour time.

**Markdown rendering**: `<div markdown [data]="message.content">` via `MarkdownModule` (ngx-markdown). NOT a custom pipe.

**Highlight pipe**: Not implemented or used. `highlight.pipe.ts` is an empty file.

**Public methods (testable)**:
- `parseSourceContent(content: string): string` — parses citation content into a filename
- `onSourceClick(citation: GroupedCitation): void` — logs and alerts citation info

---

### VoiceButtonComponent

| Kind | Name | Type | Default | Notes |
|------|------|------|---------|-------|
| @Input | `language` | `string` | `'fr'` | Passed to `transcribeWithWhisper()` |
| @Input | `disabled` | `boolean` | `false` | Disables the button |
| @Output | `transcriptFinal` | `EventEmitter<string>` | — | Emitted after successful Whisper transcription |
| @Output | `recordingChange` | `EventEmitter<boolean>` | — | `true` on start, `false` on stop |
| @Output | `error` | `EventEmitter<string>` | — | Emitted on any error |

**Component state properties (DOM-visible)**:
- `isSupported: boolean` — set from `voiceService.isRecordingSupported()` in `ngOnInit`
- `isRecording: boolean` — `true` while MediaRecorder is active
- `isProcessing: boolean` — `true` while Whisper is transcribing
- `errorMessage: string` — displayed for 5 s then cleared
- `recordingDuration: string` — `'MM:SS'` ticker, updated via `setInterval` outside Angular zone

**VoiceService methods used** (must be mocked in tests):
| Method | Signature | Notes |
|--------|-----------|-------|
| `isRecordingSupported()` | `(): boolean` | Called in `ngOnInit` |
| `startRecording()` | `(): Promise<void>` | Called on toggle |
| `stopRecording()` | `(): Promise<Blob>` | Called on toggle-stop |
| `transcribeWithWhisper()` | `(blob: Blob, lang: string): Observable<WhisperResponse>` | HTTP call to backend |
| `getErrors()` | `(): Observable<string>` | Subscribed in `ngOnInit` |

---

## Test Fixture Factories

```typescript
// Recommended in test files (or a shared test-helpers.ts)

export const mockMessage = (overrides: Partial<Message> = {}): Message => ({
  id: 'msg-1',
  role: 'user',
  content: 'Bonjour',
  timestamp: new Date('2026-05-02T14:35:00'),
  status: 'complete',
  citations: [],
  ...overrides,
});

export const mockAssistantMessage = (overrides: Partial<Message> = {}): Message =>
  mockMessage({ id: 'msg-2', role: 'assistant', content: '**Réponse**', ...overrides });

export const mockChatState = (overrides = {}): Partial<ChatState> => ({
  activeConversationId: 'conv-1',
  isStreaming: false,
  inputText: '',
  error: null,
  ...overrides,
});
```
