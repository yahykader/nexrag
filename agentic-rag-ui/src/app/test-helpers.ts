import { conversationsAdapter, ChatState, Message, Conversation } from './features/chat/store/chat.state';

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
