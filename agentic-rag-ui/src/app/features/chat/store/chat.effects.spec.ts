import { createServiceFactory, SpectatorService, SpyObject } from '@ngneat/spectator/vitest';
import { Action } from '@ngrx/store';
import { provideMockActions } from '@ngrx/effects/testing';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { ReplaySubject, of, throwError } from 'rxjs';
import { ChatEffects } from './chat.effects';
import * as ChatActions from './chat.actions';
import { StreamingApiService } from '../../../core/services/streaming-api.service';
import { buildChatState, mockConversation, mockMessage } from '../../../test-helpers';
import { selectActiveConversationId, selectActiveConversation } from './chat.selectors';

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('ChatEffects', () => {
  let spectator: SpectatorService<ChatEffects>;
  let actions$: ReplaySubject<Action>;
  let store: MockStore;
  let streamingApi: SpyObject<StreamingApiService>;

  const createService = createServiceFactory({
    service: ChatEffects,
    providers: [
      provideMockActions(() => actions$),
      provideMockStore({ initialState: { chat: buildChatState() } })
    ],
    mocks: [StreamingApiService]
  });

  beforeEach(() => {
    actions$ = new ReplaySubject<Action>(1);
    spectator = createService();
    store = spectator.inject(MockStore);
    streamingApi = spectator.inject(StreamingApiService);
  });

  afterEach(() => {
    store.resetSelectors();
  });

  // ── sendMessage$ ─────────────────────────────────────────────────────────────

  it('sendMessage$ doit dispatcher addUserMessage, addAssistantMessage et clearInputText quand une conversation est active', () => {
    store.overrideSelector(selectActiveConversationId, 'conv-1');
    store.refreshState();

    const results: Action[] = [];
    spectator.service.sendMessage$.subscribe(a => results.push(a));

    actions$.next(ChatActions.sendMessage({ content: 'Bonjour' }));

    expect(results.length).toBe(3);
    expect(results[0].type).toBe(ChatActions.addUserMessage.type);
    expect(results[1].type).toBe(ChatActions.addAssistantMessage.type);
    expect(results[2].type).toBe(ChatActions.clearInputText.type);
  });

  it('sendMessage$ doit dispatcher NO_OP si aucune conversation n\'est active', () => {
    store.overrideSelector(selectActiveConversationId, null);
    store.refreshState();

    const results: Action[] = [];
    spectator.service.sendMessage$.subscribe(a => results.push(a));

    actions$.next(ChatActions.sendMessage({ content: 'Sans conversation' }));

    expect(results.length).toBe(1);
    expect(results[0].type).toBe('NO_OP');
  });

  // ── startStreaming$ ──────────────────────────────────────────────────────────

  it('startStreaming$ doit mapper connected → streamConnected, token → streamToken, complete → streamComplete', () => {
    const conv = mockConversation({ messages: [mockMessage({ role: 'user', content: 'Question RAG' })] });
    store.overrideSelector(selectActiveConversationId, conv.id);
    store.overrideSelector(selectActiveConversation, conv);
    store.refreshState();

    streamingApi.stream.mockReturnValue(of(
      { type: 'connected' as const, sessionId: 'sess-1' },
      { type: 'token' as const, text: 'Réponse' },
      { type: 'complete' as const }
    ));

    const results: Action[] = [];
    spectator.service.startStreaming$.subscribe(a => results.push(a));

    actions$.next(ChatActions.addAssistantMessage({ messageId: 'asst-1' }));

    expect(results.length).toBe(3);
    expect(results[0].type).toBe(ChatActions.streamConnected.type);
    expect(results[1].type).toBe(ChatActions.streamToken.type);
    expect(results[2].type).toBe(ChatActions.streamComplete.type);
  });

  it('startStreaming$ doit appeler stream() avec le contenu du dernier message utilisateur et le conversationId', () => {
    const conv = mockConversation({ id: 'conv-1', messages: [mockMessage({ role: 'user', content: 'Ma question' })] });
    store.overrideSelector(selectActiveConversationId, 'conv-1');
    store.overrideSelector(selectActiveConversation, conv);
    store.refreshState();

    streamingApi.stream.mockReturnValue(of({ type: 'complete' as const }));

    spectator.service.startStreaming$.subscribe();
    actions$.next(ChatActions.addAssistantMessage({ messageId: 'asst-1' }));

    expect(streamingApi.stream).toHaveBeenCalledWith({
      query: 'Ma question',
      conversationId: 'conv-1'
    });
  });

  it('startStreaming$ doit dispatcher streamError si la conversation active ne contient aucun message utilisateur', () => {
    const emptyConv = mockConversation({ messages: [] });
    store.overrideSelector(selectActiveConversationId, emptyConv.id);
    store.overrideSelector(selectActiveConversation, emptyConv);
    store.refreshState();

    const results: Action[] = [];
    spectator.service.startStreaming$.subscribe(a => results.push(a));

    actions$.next(ChatActions.addAssistantMessage({ messageId: 'asst-1' }));

    expect(results.length).toBe(1);
    expect(results[0].type).toBe(ChatActions.streamError.type);
  });

  it('startStreaming$ doit dispatcher streamError si stream() lève une erreur (catchError)', () => {
    const conv = mockConversation({ messages: [mockMessage({ role: 'user', content: 'Question' })] });
    store.overrideSelector(selectActiveConversationId, conv.id);
    store.overrideSelector(selectActiveConversation, conv);
    store.refreshState();

    streamingApi.stream.mockReturnValue(throwError(() => new Error('Network failure')));

    const results: Action[] = [];
    spectator.service.startStreaming$.subscribe(a => results.push(a));

    actions$.next(ChatActions.addAssistantMessage({ messageId: 'asst-1' }));

    expect(results.length).toBe(1);
    expect(results[0].type).toBe(ChatActions.streamError.type);
  });

  // ── cancelStream$ ────────────────────────────────────────────────────────────

  it('cancelStream$ doit dispatcher cancelStreamSuccess immédiatement si streamSessionId est null, sans appeler l\'API', () => {
    store.setState({ chat: { ...buildChatState(), streamSessionId: null } });

    const results: Action[] = [];
    spectator.service.cancelStream$.subscribe(a => results.push(a));

    actions$.next(ChatActions.cancelStream());

    expect(results.length).toBe(1);
    expect(results[0].type).toBe(ChatActions.cancelStreamSuccess.type);
    expect(streamingApi.cancelStream).not.toHaveBeenCalled();
  });
});
