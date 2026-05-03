import { Component } from '@angular/core';
import { isObservable } from 'rxjs';
import { createComponentFactory, mockProvider, Spectator } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { ChatPageComponent } from './chat-page.component';
import { ChatInterfaceComponent } from '../../components/chat-interface/chat-interface.component';
import { ConfirmationService } from '../../../../core/services/confirmation.service';
import { conversationsAdapter, initialChatState, Conversation } from '../../store/chat.state';
import * as ChatActions from '../../store/chat.actions';
import * as ChatSelectors from '../../store/chat.selectors';
import { mockConversation } from '../../../../test-helpers';

// ─── Stub ─────────────────────────────────────────────────────────────────────

@Component({ selector: 'app-chat-interface', template: '', standalone: true })
class ChatInterfaceStub {}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function mockChatState(conversations: Conversation[], activeId: string | null = null) {
  return {
    ...initialChatState,
    conversations: conversationsAdapter.setAll(conversations, conversationsAdapter.getInitialState()),
    activeConversationId: activeId,
  };
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('ChatPageComponent', () => {
  let spectator: Spectator<ChatPageComponent>;
  let store: MockStore;

  const createComponent = createComponentFactory({
    component: ChatPageComponent,
    overrideComponents: [[
      ChatPageComponent,
      { remove: { imports: [ChatInterfaceComponent] }, add: { imports: [ChatInterfaceStub] } }
    ]],
    providers: [
      provideMockStore({ initialState: { chat: mockChatState([], null) } }),
      mockProvider(ConfirmationService),
    ],
    detectChanges: false,
  });

  beforeEach(() => {
    spectator = createComponent();
    store = spectator.inject(MockStore);
    store.overrideSelector(ChatSelectors.selectConversations, []);
    store.overrideSelector(ChatSelectors.selectActiveConversationId, null);
    store.refreshState();
    spectator.detectChanges();
  });

  afterEach(() => {
    store.resetSelectors();
    vi.restoreAllMocks();
  });

  // ── US2 — Rendu depuis le store ───────────────────────────────────────────

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  it('doit s\'abonner à conversations$ depuis le store', () => {
    expect(isObservable(spectator.component.conversations$)).toBe(true);
  });

  it('doit s\'abonner à activeConversationId$ depuis le store', () => {
    expect(isObservable(spectator.component.activeConversationId$)).toBe(true);
  });

  it('doit afficher deux items quand le store contient deux conversations', () => {
    const conversations = [
      mockConversation({ id: 'conv-1' }),
      mockConversation({ id: 'conv-2', title: 'Second' }),
    ];
    store.overrideSelector(ChatSelectors.selectConversations, conversations);
    store.refreshState();
    spectator.detectChanges();

    expect(spectator.queryAll('.conversation-item').length).toBe(2);
  });

  it('doit afficher l\'état vide quand conversations est vide', () => {
    store.overrideSelector(ChatSelectors.selectConversations, []);
    store.refreshState();
    spectator.detectChanges();

    expect(spectator.query('.empty-conversations')).toBeTruthy();
  });

  it('doit inclure app-chat-interface dans le template', () => {
    expect(spectator.query('app-chat-interface')).not.toBeNull();
  });

  // ── US3 — Gestion des conversations ──────────────────────────────────────

  it('doit dispatcher createConversation au click sur Nouveau', () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');

    spectator.click('.btn-primary');

    expect(dispatchSpy).toHaveBeenCalledWith(ChatActions.createConversation());
  });

  it('doit dispatcher setActiveConversation avec le bon id au click sur une conversation', () => {
    const conv = mockConversation({ id: 'conv-1' });
    store.overrideSelector(ChatSelectors.selectConversations, [conv]);
    store.refreshState();
    spectator.detectChanges();
    const dispatchSpy = vi.spyOn(store, 'dispatch');

    spectator.click('.conversation-item');

    expect(dispatchSpy).toHaveBeenCalledWith(
      ChatActions.setActiveConversation({ conversationId: 'conv-1' })
    );
  });

  it('doit dispatcher deleteConversation quand ConfirmationService retourne true', () => {
    const conv = mockConversation({ id: 'conv-1' });
    store.overrideSelector(ChatSelectors.selectConversations, [conv]);
    store.refreshState();
    spectator.detectChanges();
    vi.mocked(spectator.inject(ConfirmationService).confirm).mockReturnValue(true);
    const dispatchSpy = vi.spyOn(store, 'dispatch');

    spectator.click('.conversation-delete');

    expect(dispatchSpy).toHaveBeenCalledWith(
      ChatActions.deleteConversation({ conversationId: 'conv-1' })
    );
  });

  it('ne doit pas dispatcher deleteConversation quand ConfirmationService retourne false', () => {
    const conv = mockConversation({ id: 'conv-1' });
    store.overrideSelector(ChatSelectors.selectConversations, [conv]);
    store.refreshState();
    spectator.detectChanges();
    vi.mocked(spectator.inject(ConfirmationService).confirm).mockReturnValue(false);
    const dispatchSpy = vi.spyOn(store, 'dispatch');

    spectator.click('.conversation-delete');

    expect(dispatchSpy).not.toHaveBeenCalledWith(
      ChatActions.deleteConversation({ conversationId: 'conv-1' })
    );
  });

  // ── US4 — Bascule de la barre latérale ───────────────────────────────────

  it('doit passer sidebarCollapsed à true au premier appel de toggleSidebar()', () => {
    spectator.click('.sidebar-toggle');

    expect(spectator.component.sidebarCollapsed).toBe(true);
    expect(spectator.query('.chat-sidebar')?.classList.contains('collapsed')).toBe(true);
  });

  it('doit passer sidebarCollapsed à false au deuxième appel de toggleSidebar()', () => {
    spectator.click('.sidebar-toggle');
    spectator.click('.sidebar-toggle');

    expect(spectator.component.sidebarCollapsed).toBe(false);
    expect(spectator.query('.chat-sidebar')?.classList.contains('collapsed')).toBe(false);
  });

  // ── Intégration ───────────────────────────────────────────────────────────

  it('[INTÉGRATION] doit afficher l\'élément app-chat-interface dans le template complet', () => {
    const conv = mockConversation({ id: 'conv-1' });
    store.overrideSelector(ChatSelectors.selectConversations, [conv]);
    store.refreshState();
    spectator.detectChanges();

    const chatInterface = spectator.query('app-chat-interface');
    expect(chatInterface).not.toBeNull();
    expect(spectator.query('.chat-main')?.contains(chatInterface!)).toBe(true);
  });

  it('[INTÉGRATION] dispatch complet : click conversation → setActiveConversation dispatché', () => {
    const conversations = [
      mockConversation({ id: 'conv-1' }),
      mockConversation({ id: 'conv-2', title: 'Second' }),
    ];
    store.overrideSelector(ChatSelectors.selectConversations, conversations);
    store.refreshState();
    spectator.detectChanges();

    const dispatchedActions: unknown[] = [];
    const sub = store.scannedActions$.subscribe(action => dispatchedActions.push(action));

    spectator.click('.conversation-item');
    sub.unsubscribe();

    expect(dispatchedActions).toContainEqual(
      ChatActions.setActiveConversation({ conversationId: 'conv-1' })
    );
  });
});
