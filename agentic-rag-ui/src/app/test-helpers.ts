import { conversationsAdapter, ChatState, Message, Conversation } from './features/chat/store/chat.state';
import { UploadFile } from './features/ingestion/store/ingestion/ingestion.state';
import { DeleteOperation } from './features/ingestion/store/crud/crud.state';
import { UploadProgress } from './core/services/websocket-progress.service';

export function buildChatState(overrides: Partial<ChatState> = {}): ChatState {
  return {
    conversations: conversationsAdapter.getInitialState(),
    activeConversationId: null,
    isStreaming: false,
    streamingMessageId: null,
    streamSessionId: null,
    inputText: '',
    loading: false,
    error: null,
    ...overrides
  };
}

export function mockMessage(overrides: Partial<Message> = {}): Message {
  return {
    id: 'msg-1',
    role: 'user',
    content: 'Test message',
    timestamp: new Date('2026-01-01T00:00:00.000Z'),
    status: 'complete',
    ...overrides
  };
}

export function mockConversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    id: 'conv-1',
    title: 'Test conversation',
    messages: [],
    createdAt: new Date('2026-01-01T00:00:00.000Z'),
    updatedAt: new Date('2026-01-01T00:00:00.000Z'),
    ...overrides
  };
}

export function mockUploadFile(overrides: Partial<UploadFile> = {}): UploadFile {
  return {
    id: 'upload_test_1',
    file: new File(['content'], 'test.pdf', { type: 'application/pdf' }),
    progress: 0,
    status: 'pending',
    ...overrides
  };
}

export function mockDeleteOperation(overrides: Partial<DeleteOperation> = {}): DeleteOperation {
  return {
    id: 'del-op-1',
    type: 'file',
    targetId: 'emb-1',
    status: 'pending',
    timestamp: new Date('2026-01-01T00:00:00.000Z'),
    ...overrides
  };
}

export function mockUploadProgress(overrides: Partial<UploadProgress> = {}): UploadProgress {
  return {
    batchId: 'batch-test-1',
    filename: 'test.pdf',
    stage: 'PROCESSING',
    progressPercentage: 50,
    message: 'Processing...',
    ...overrides
  };
}
