import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { ChatResolver } from './chat.resolver';
import { conversationsAdapter, initialChatState } from '../store/chat.state';
import * as ChatActions from '../store/chat.actions';
import * as ChatSelectors from '../store/chat.selectors';

function mockChatState(activeId: string | null) {
  return {
    ...initialChatState,
    conversations: conversationsAdapter.getInitialState(),
    activeConversationId: activeId,
  };
}

describe('ChatResolver', () => {
  let spectator: SpectatorService<ChatResolver>;
  let store: MockStore;

  const createService = createServiceFactory({
    service: ChatResolver,
    providers: [
      provideMockStore({ initialState: { chat: mockChatState(null) } }),
    ],
  });

  beforeEach(() => {
    spectator = createService();
    store = spectator.inject(MockStore);
  });

  afterEach(() => {
    store.resetSelectors();
    vi.restoreAllMocks();
  });

  it('doit dispatcher createConversation quand aucune conversation active', () => {
    store.overrideSelector(ChatSelectors.selectActiveConversationId, null);
    store.refreshState();
    const dispatchSpy = vi.spyOn(store, 'dispatch');

    spectator.service.resolve().subscribe();

    expect(dispatchSpy).toHaveBeenCalledWith(ChatActions.createConversation());
  });

  it('ne doit pas dispatcher createConversation quand une conversation est déjà active', () => {
    store.overrideSelector(ChatSelectors.selectActiveConversationId, 'conv-1');
    store.refreshState();
    const dispatchSpy = vi.spyOn(store, 'dispatch');

    spectator.service.resolve().subscribe();

    expect(dispatchSpy).not.toHaveBeenCalledWith(ChatActions.createConversation());
  });

  it('doit émettre une fois et compléter sans bloquer la navigation', () => {
    store.overrideSelector(ChatSelectors.selectActiveConversationId, null);
    store.refreshState();
    let emitCount = 0;
    let completed = false;

    spectator.service.resolve().subscribe({
      next: () => emitCount++,
      complete: () => { completed = true; },
    });

    expect(emitCount).toBe(1);
    expect(completed).toBe(true);
  });
});
