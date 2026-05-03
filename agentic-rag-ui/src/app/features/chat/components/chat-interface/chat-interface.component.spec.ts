import { Component, Input, Output, EventEmitter, NO_ERRORS_SCHEMA } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { ChatInterfaceComponent } from './chat-interface.component';
import { MessageItemComponent } from '../message-item/message-item.component';
import { MessageInputComponent } from '../message-input/message-input.component';
import * as ChatActions from '../../store/chat.actions';
import * as ChatSelectors from '../../store/chat.selectors';
import { Message } from '../../store/chat.state';
import { buildChatState, mockMessage } from '../../../../test-helpers';

// ─── Stubs ────────────────────────────────────────────────────────────────────

@Component({ selector: 'app-message-item', template: '', standalone: true })
class MockMessageItemComponent {
  @Input() message!: Message;
  @Input() isStreaming = false;
}

@Component({ selector: 'app-message-input', template: '', standalone: true })
class MockMessageInputComponent {
  @Input() inputText = '';
  @Input() isStreaming = false;
  @Output() send = new EventEmitter<string>();
  @Output() cancel = new EventEmitter<void>();
  @Output() inputChange = new EventEmitter<string>();
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('ChatInterfaceComponent', () => {
  let spectator: Spectator<ChatInterfaceComponent>;
  let store: MockStore;

  const createComponent = createComponentFactory({
    component: ChatInterfaceComponent,
    providers: [
      provideMockStore({
        initialState: { chat: buildChatState({ activeConversationId: 'conv-1' }) }
      })
    ],
    overrideComponents: [
      [ChatInterfaceComponent, {
        remove: { imports: [MessageItemComponent, MessageInputComponent] },
        add: { imports: [MockMessageItemComponent, MockMessageInputComponent] }
      }]
    ],
    detectChanges: false,
  });

  beforeEach(() => {
    spectator = createComponent();
    store = spectator.inject(MockStore);
    store.overrideSelector(ChatSelectors.selectActiveMessages, []);
    store.overrideSelector(ChatSelectors.selectIsStreaming, false);
    store.overrideSelector(ChatSelectors.selectInputText, '');
    store.overrideSelector(ChatSelectors.selectError, null);
    store.overrideSelector(ChatSelectors.selectActiveConversationId, 'conv-1');
    store.refreshState();
    spectator.detectChanges();
  });

  afterEach(() => {
    store.resetSelectors();
    vi.restoreAllMocks();
  });

  // ── Smoke ─────────────────────────────────────────────────────────────────

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  // ── Messages list ─────────────────────────────────────────────────────────

  it('doit afficher 3 messages quand le store en contient 3', () => {
    const messages = [
      mockMessage({ id: 'msg-1' }),
      mockMessage({ id: 'msg-2' }),
      mockMessage({ id: 'msg-3' }),
    ];
    store.overrideSelector(ChatSelectors.selectActiveMessages, messages);
    store.refreshState();
    spectator.detectChanges();

    expect(spectator.queryAll('app-message-item').length).toBe(3);
  });

  it('doit afficher l\'état vide sans erreur quand la liste de messages est vide', () => {
    store.overrideSelector(ChatSelectors.selectActiveMessages, []);
    store.refreshState();
    spectator.detectChanges();

    expect(spectator.queryAll('app-message-item').length).toBe(0);
    expect(spectator.query('.empty-state')).toBeTruthy();
  });

  // ── Streaming state ───────────────────────────────────────────────────────

  it('doit synchroniser isStreaming avec l\'état du store', () => {
    store.overrideSelector(ChatSelectors.selectIsStreaming, true);
    store.refreshState();
    spectator.detectChanges();

    expect(spectator.component.isStreaming).toBe(true);
  });

  // ── Template structure ────────────────────────────────────────────────────

  it('doit contenir app-message-input dans le template', () => {
    expect(spectator.query('app-message-input')).toBeTruthy();
  });

  // ── Init guard ────────────────────────────────────────────────────────────

  it('ne doit pas dispatcher createConversation si activeConversationId est défini', () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    store.overrideSelector(ChatSelectors.selectActiveConversationId, 'conv-1');
    store.refreshState();
    spectator.detectChanges();

    expect(dispatchSpy).not.toHaveBeenCalledWith(ChatActions.createConversation());
  });

  // ── Init — createConversation ─────────────────────────────────────────────

  it('doit dispatcher createConversation quand activeConversationId est null', () => {
    spectator = createComponent();
    store = spectator.inject(MockStore);
    store.overrideSelector(ChatSelectors.selectActiveMessages, []);
    store.overrideSelector(ChatSelectors.selectIsStreaming, false);
    store.overrideSelector(ChatSelectors.selectInputText, '');
    store.overrideSelector(ChatSelectors.selectError, null);
    store.overrideSelector(ChatSelectors.selectActiveConversationId, null);
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    store.refreshState();
    spectator.detectChanges();
    expect(dispatchSpy).toHaveBeenCalledWith(ChatActions.createConversation());
  });

  // ── Dispatch actions ──────────────────────────────────────────────────────

  it('doit dispatcher cancelStream quand le streaming est annulé', () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    spectator.triggerEventHandler('app-message-input', 'cancel', undefined);
    expect(dispatchSpy).toHaveBeenCalledWith(ChatActions.cancelStream());
  });

  it('doit dispatcher updateInputText quand le texte du champ change', () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    spectator.triggerEventHandler('app-message-input', 'inputChange', 'Salut');
    expect(dispatchSpy).toHaveBeenCalledWith(ChatActions.updateInputText({ text: 'Salut' }));
  });

  // ── Integration ───────────────────────────────────────────────────────────

  it('[INTÉGRATION] doit dispatcher SendMessage quand l\'utilisateur soumet un message', () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    spectator.triggerEventHandler('app-message-input', 'send', 'Bonjour');

    expect(dispatchSpy).toHaveBeenCalledWith(ChatActions.sendMessage({ content: 'Bonjour' }));
  });
});
