import {
  selectChatState,
  selectConversations,
  selectConversationsDictionary,
  selectActiveConversationId,
  selectActiveConversation,
  selectActiveMessages,
  selectIsStreaming,
  selectInputText,
  selectError,
  selectConversationById,
  selectRecentConversations
} from './chat.selectors';
import { conversationsAdapter, ChatState, Message, Conversation } from './chat.state';
import { buildChatState, mockMessage, mockConversation } from '../../../test-helpers';

// ─── Local helpers ────────────────────────────────────────────────────────────

function buildAppState(overrides: Partial<ChatState> = {}): { chat: ChatState } {
  return { chat: { ...buildChatState(), ...overrides } };
}

function appStateWithActiveConversation(
  messages: Message[] = [],
  chatOverrides: Partial<ChatState> = {}
): { chat: ChatState } {
  const conv = mockConversation({ messages });
  return {
    chat: {
      ...buildChatState(),
      conversations: conversationsAdapter.addOne(conv, conversationsAdapter.getInitialState()),
      activeConversationId: conv.id,
      ...chatOverrides
    }
  };
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('ChatSelectors', () => {

  // ── Default (empty) state ─────────────────────────────────────────────────

  it('selectChatState doit retourner la slice chat depuis l\'état global', () => {
    const appState = buildAppState();
    expect(selectChatState(appState)).toEqual(appState.chat);
  });

  it('selectConversations doit retourner un tableau vide pour l\'état initial', () => {
    expect(selectConversations(buildAppState())).toEqual([]);
  });

  it('selectActiveConversationId doit retourner null pour l\'état initial', () => {
    expect(selectActiveConversationId(buildAppState())).toBeNull();
  });

  it('selectIsStreaming doit retourner false pour l\'état initial', () => {
    expect(selectIsStreaming(buildAppState())).toBe(false);
  });

  it('selectError doit retourner null pour l\'état initial', () => {
    expect(selectError(buildAppState())).toBeNull();
  });

  it('selectInputText doit retourner une chaîne vide pour l\'état initial', () => {
    expect(selectInputText(buildAppState())).toBe('');
  });

  // ── Active conversation selectors ─────────────────────────────────────────

  it('selectActiveMessages doit retourner [] si aucune conversation n\'est active', () => {
    expect(selectActiveMessages(buildAppState({ activeConversationId: null }))).toEqual([]);
  });

  it('selectActiveMessages doit retourner les messages de la conversation active', () => {
    const appState = appStateWithActiveConversation([mockMessage()]);
    expect(selectActiveMessages(appState).length).toBe(1);
    expect(selectActiveMessages(appState)[0].content).toBe('Test message');
  });

  it('selectActiveConversation doit retourner la conversation active si elle existe', () => {
    const appState = appStateWithActiveConversation();
    const result = selectActiveConversation(appState);
    expect(result).toBeDefined();
    expect(result?.id).toBe('conv-1');
  });

  it('selectActiveConversation doit retourner undefined si aucune conversation n\'est active', () => {
    expect(selectActiveConversation(buildAppState())).toBeUndefined();
  });

  it('selectConversationsDictionary doit retourner un objet indexé par id', () => {
    const appState = appStateWithActiveConversation();
    const dict = selectConversationsDictionary(appState);
    expect(dict['conv-1']).toBeDefined();
    expect(dict['conv-1']?.id).toBe('conv-1');
  });

  // ── UI state selectors ─────────────────────────────────────────────────────

  it('selectIsStreaming doit retourner true quand le streaming est actif', () => {
    const appState = buildAppState({ isStreaming: true });
    expect(selectIsStreaming(appState)).toBe(true);
  });

  it('selectError doit retourner le message d\'erreur stocké', () => {
    const appState = buildAppState({ error: 'Network error' });
    expect(selectError(appState)).toBe('Network error');
  });

  it('selectInputText doit retourner le texte en cours de saisie', () => {
    const appState = buildAppState({ inputText: 'mon message en cours' });
    expect(selectInputText(appState)).toBe('mon message en cours');
  });

  // ── Derived selectors ──────────────────────────────────────────────────────

  it('selectRecentConversations doit retourner au maximum 5 conversations', () => {
    const convs = Array.from({ length: 7 }, (_, i) =>
      mockConversation({ id: `conv-${i}`, title: `Conversation ${i}`, updatedAt: new Date(2026, 0, i + 1) })
    );
    const conversations = convs.reduce(
      (acc, c) => conversationsAdapter.addOne(c, acc),
      conversationsAdapter.getInitialState()
    );
    const appState = buildAppState({ conversations });

    const result = selectRecentConversations(appState);
    expect(result.length).toBe(5);
  });

  it('selectConversations doit retourner les conversations triées par updatedAt décroissant', () => {
    const old = mockConversation({ id: 'old', updatedAt: new Date(2026, 0, 1) });
    const recent = mockConversation({ id: 'recent', updatedAt: new Date(2026, 0, 10) });
    const conversations = conversationsAdapter.addMany([old, recent], conversationsAdapter.getInitialState());
    const appState = buildAppState({ conversations });

    const result = selectConversations(appState);
    expect(result[0].id).toBe('recent');
    expect(result[1].id).toBe('old');
  });

  it('selectConversationById doit retourner la conversation si elle existe', () => {
    const appState = appStateWithActiveConversation();
    const result = selectConversationById('conv-1')(appState);
    expect(result?.id).toBe('conv-1');
  });

  it('selectConversationById doit retourner undefined pour un id inconnu', () => {
    const appState = buildAppState();
    const result = selectConversationById('unknown')(appState);
    expect(result).toBeUndefined();
  });
});
