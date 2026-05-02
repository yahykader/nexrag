import { chatReducer } from './chat.reducer';
import { initialChatState, conversationsAdapter, ChatState, Message, Conversation } from './chat.state';
import * as ChatActions from './chat.actions';
import { buildChatState, mockMessage, mockConversation } from '../../../test-helpers';

// ─── Local helpers ────────────────────────────────────────────────────────────

function stateWithConv(messages: Message[] = [], extras: Partial<ChatState> = {}): ChatState {
  const conv = mockConversation({ messages });
  return {
    ...buildChatState(),
    conversations: conversationsAdapter.addOne(conv, conversationsAdapter.getInitialState()),
    activeConversationId: conv.id,
    ...extras
  };
}

function stateStreaming(content = 'Hello'): ChatState {
  const assistantMsg = mockMessage({ id: 'asst-1', role: 'assistant', content, status: 'streaming' });
  return stateWithConv([assistantMsg], { isStreaming: true, streamingMessageId: 'asst-1' });
}

function getActiveMessages(state: ChatState): Message[] {
  const conv = state.conversations.entities[state.activeConversationId!];
  return conv?.messages ?? [];
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('ChatReducer', () => {
  beforeEach(() => {
    vi.spyOn(console, 'log').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ── Initial state ─────────────────────────────────────────────────────────

  it('doit retourner l\'état initial', () => {
    const state = chatReducer(undefined, { type: '@@INIT' });
    expect(state).toEqual(initialChatState);
  });

  // ── Conversation lifecycle ─────────────────────────────────────────────────

  it('createConversation doit ajouter une conversation et la définir comme active', () => {
    const state = chatReducer(buildChatState(), ChatActions.createConversation());
    expect(state.conversations.ids.length).toBe(1);
    const newId = state.conversations.ids[0] as string;
    expect(state.activeConversationId).toBe(newId);
    expect(state.conversations.entities[newId]?.title).toBe('Nouvelle conversation');
  });

  it('loadConversationsSuccess doit remplacer toutes les conversations existantes (setAll)', () => {
    const existing = mockConversation({ id: 'old-1' });
    const initial = stateWithConv([], {});
    const conv2 = mockConversation({ id: 'new-2', title: 'Conv 2' });
    const conv3 = mockConversation({ id: 'new-3', title: 'Conv 3' });

    const state = chatReducer(initial, ChatActions.loadConversationsSuccess({ conversations: [conv2, conv3] }));

    expect(state.conversations.ids.length).toBe(2);
    expect(state.conversations.entities['new-2']).toBeDefined();
    expect(state.conversations.entities['new-3']).toBeDefined();
    expect(state.conversations.entities['conv-1']).toBeUndefined();
    expect(state.loading).toBe(false);
  });

  it('deleteConversation doit retirer la conv et effacer activeConversationId si elle était active', () => {
    const initial = stateWithConv();
    expect(initial.activeConversationId).toBe('conv-1');

    const state = chatReducer(initial, ChatActions.deleteConversation({ conversationId: 'conv-1' }));

    expect(state.conversations.ids).not.toContain('conv-1');
    expect(state.activeConversationId).toBeNull();
  });

  it('deleteConversation doit conserver activeConversationId si une autre conv est supprimée', () => {
    const conv2 = mockConversation({ id: 'conv-2' });
    const initial: ChatState = {
      ...stateWithConv(),
      conversations: conversationsAdapter.addOne(conv2, conversationsAdapter.addOne(mockConversation(), conversationsAdapter.getInitialState())),
      activeConversationId: 'conv-1'
    };

    const state = chatReducer(initial, ChatActions.deleteConversation({ conversationId: 'conv-2' }));

    expect(state.activeConversationId).toBe('conv-1');
  });

  // ── Message creation ───────────────────────────────────────────────────────

  it('addUserMessage doit ajouter le message à la fin de la conversation active', () => {
    const initial = stateWithConv();
    const msg = mockMessage();

    const state = chatReducer(initial, ChatActions.addUserMessage({ message: msg }));

    const messages = getActiveMessages(state);
    expect(messages.length).toBe(1);
    expect(messages[0]).toEqual(msg);
  });

  it('addUserMessage doit définir le titre depuis le contenu du premier message', () => {
    const initial = stateWithConv();
    const msg = mockMessage({ content: 'Ma première question sur le RAG' });

    const state = chatReducer(initial, ChatActions.addUserMessage({ message: msg }));

    const conv = state.conversations.entities[state.activeConversationId!];
    expect(conv?.title).toBe('Ma première question sur le RAG');
  });

  it('addUserMessage doit être sans effet si aucune conversation n\'est active', () => {
    const initial = buildChatState();
    const state = chatReducer(initial, ChatActions.addUserMessage({ message: mockMessage() }));
    expect(state).toEqual(initial);
  });

  it('addAssistantMessage doit ajouter un message assistant vide et passer isStreaming à true', () => {
    const initial = stateWithConv();

    const state = chatReducer(initial, ChatActions.addAssistantMessage({ messageId: 'asst-1' }));

    const messages = getActiveMessages(state);
    expect(messages.length).toBe(1);
    expect(messages[0].id).toBe('asst-1');
    expect(messages[0].role).toBe('assistant');
    expect(messages[0].content).toBe('');
    expect(messages[0].status).toBe('streaming');
    expect(state.isStreaming).toBe(true);
    expect(state.streamingMessageId).toBe('asst-1');
  });

  // ── Streaming tokens ───────────────────────────────────────────────────────

  it('streamToken doit accumuler le texte dans le message en cours de streaming', () => {
    const initial = stateStreaming('Hello');

    const state = chatReducer(initial, ChatActions.streamToken({ event: { type: 'token', text: ' World' } }));

    const messages = getActiveMessages(state);
    const assistantMsg = messages.find(m => m.id === 'asst-1');
    expect(assistantMsg?.content).toBe('Hello World');
  });

  it('streamToken doit être ignoré si streamingMessageId est null (late-chunk guard)', () => {
    const initial = stateWithConv([], { isStreaming: false, streamingMessageId: null });

    const state = chatReducer(initial, ChatActions.streamToken({ event: { type: 'token', text: 'orphan' } }));

    expect(state).toEqual(initial);
  });

  // ── Stream complete ────────────────────────────────────────────────────────

  it('streamComplete doit marquer le message complete et extraire les citations', () => {
    const initial = stateStreaming('Réponse complète');
    const completeEvent = {
      type: 'complete' as const,
      response: { sources: [{ file: 'doc.pdf', page: '3' }, { file: 'report.docx', page: null }] }
    };

    const state = chatReducer(initial, ChatActions.streamComplete({ event: completeEvent }));

    const messages = getActiveMessages(state);
    const assistantMsg = messages.find(m => m.id === 'asst-1');
    expect(assistantMsg?.status).toBe('complete');
    expect(assistantMsg?.citations?.length).toBe(2);
    expect(assistantMsg?.citations?.[0].sourceFile).toBe('doc.pdf');
    expect(assistantMsg?.citations?.[1].sourceFile).toBe('report.docx');
  });

  it('streamComplete doit passer isStreaming à false et réinitialiser les champs streaming', () => {
    const initial = stateStreaming();
    const state = chatReducer(initial, ChatActions.streamComplete({ event: { type: 'complete' } }));

    expect(state.isStreaming).toBe(false);
    expect(state.streamingMessageId).toBeNull();
    expect(state.streamSessionId).toBeNull();
  });

  it('streamComplete doit être sans effet si streamingMessageId est null', () => {
    const initial = stateWithConv([], { isStreaming: false, streamingMessageId: null });
    const state = chatReducer(initial, ChatActions.streamComplete({ event: { type: 'complete' } }));
    expect(state).toEqual(initial);
  });

  // ── Stream error ───────────────────────────────────────────────────────────

  it('streamError doit passer isStreaming à false, stocker l\'erreur et ne pas retenter', () => {
    const initial = stateStreaming();

    const state = chatReducer(initial, ChatActions.streamError({ error: 'Connection lost' }));

    expect(state.isStreaming).toBe(false);
    expect(state.streamingMessageId).toBeNull();
    expect(state.streamSessionId).toBeNull();
    expect(state.error).toBe('Connection lost');
  });

  it('cancelStreamSuccess doit réinitialiser tous les champs de streaming', () => {
    const initial: ChatState = { ...buildChatState(), isStreaming: true, streamingMessageId: 'asst-1', streamSessionId: 'sess-1' };

    const state = chatReducer(initial, ChatActions.cancelStreamSuccess());

    expect(state.isStreaming).toBe(false);
    expect(state.streamingMessageId).toBeNull();
    expect(state.streamSessionId).toBeNull();
  });

  // ── Input & error ──────────────────────────────────────────────────────────

  it('updateInputText doit mettre à jour inputText', () => {
    const initial = buildChatState();
    const state = chatReducer(initial, ChatActions.updateInputText({ text: 'nouvelle requête' }));
    expect(state.inputText).toBe('nouvelle requête');
  });

  it('clearInputText doit remettre inputText à une chaîne vide', () => {
    const initial = buildChatState({ inputText: 'some query' });
    const state = chatReducer(initial, ChatActions.clearInputText());
    expect(state.inputText).toBe('');
  });

  it('clearError doit remettre error à null', () => {
    const initial = buildChatState({ error: 'previous error' });
    const state = chatReducer(initial, ChatActions.clearError());
    expect(state.error).toBeNull();
  });

  it('streamConnected doit stocker le streamSessionId', () => {
    const initial = buildChatState();
    const state = chatReducer(initial, ChatActions.streamConnected({ event: { type: 'connected', sessionId: 'sess-42' } }));
    expect(state.streamSessionId).toBe('sess-42');
  });
});
