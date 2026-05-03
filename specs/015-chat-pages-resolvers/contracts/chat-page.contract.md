# Component Contract: ChatPageComponent

**Source**: `agentic-rag-ui/src/app/features/chat/pages/chat-page/chat-page.component.ts`  
**Spec**: `chat-page.component.spec.ts` (co-located)  
**Phase**: 8

---

## Inputs

None — `ChatPageComponent` is a routed page component with no `@Input()` bindings.

## Outputs

None — all interactions go through NgRx store dispatch.

## Store Bindings (read)

| Property | Selector | Type | When read |
|---|---|---|---|
| `conversations$` | `ChatSelectors.selectConversations` | `Observable<Conversation[]>` | Constructor |
| `activeConversationId$` | `ChatSelectors.selectActiveConversationId` | `Observable<string \| null>` | Constructor |

## Store Bindings (write — dispatched actions)

| Method | Action dispatched | Condition |
|---|---|---|
| `onNewConversation()` | `ChatActions.createConversation()` | Always |
| `onSelectConversation(id)` | `ChatActions.setActiveConversation({ conversationId: id })` | Always |
| `onDeleteConversation(id, event)` | `ChatActions.deleteConversation({ conversationId: id })` | Only if `ConfirmationService.confirm()` returns `true` |

## Service Dependencies

| Service | Injection token | Role |
|---|---|---|
| `Store` | `@ngrx/store Store` | State reads and dispatches |
| `ConfirmationService` | `ConfirmationService` | Wraps `window.confirm`; injected for testability |

## Template Contracts

| Element | Selector / binding | Verified by |
|---|---|---|
| Sidebar container | `.chat-sidebar[class.collapsed]="sidebarCollapsed"` | US4 scenarios |
| Conversation list | `*ngFor="let conv of conversations$ \| async"` | US2.3, US2.4 |
| Active item highlight | `[class.active]="(activeConversationId$ \| async) === conv.id"` | US2 integration |
| "Nouveau" button | `(click)="onNewConversation()"` | US3.1 |
| Conversation select | `(click)="onSelectConversation(conv.id)"` | US3.2 |
| Delete button | `(click)="onDeleteConversation(conv.id, $event)"` | US3.3, US3.4 |
| Empty state block | `*ngIf="(conversations$ \| async)?.length === 0"` | US2.4 |
| Sidebar toggle | `(click)="toggleSidebar()"` | US4.1, US4.2 |
| Chat interface | `<app-chat-interface>` | US2.5, integration |

## Isolation Strategy for Tests

```typescript
// Stub replaces real ChatInterfaceComponent
@Component({ selector: 'app-chat-interface', template: '', standalone: true })
class ChatInterfaceStub {}

const createComponent = createComponentFactory({
  component: ChatPageComponent,
  overrideComponents: [
    [ChatPageComponent, { remove: { imports: [ChatInterfaceComponent] }, add: { imports: [ChatInterfaceStub] } }]
  ],
  providers: [
    provideMockStore({ initialState: { chat: mockChatState([], null) } }),
    mockProvider(ConfirmationService),
  ],
});
```

## Test File Structure

```
describe('ChatPageComponent') {
  // US2 — Store subscription & rendering
  it('doit créer le composant')
  it('doit s\'abonner à conversations$ au démarrage')
  it('doit s\'abonner à activeConversationId$ au démarrage')
  it('doit afficher deux items quand le store contient deux conversations')
  it('doit afficher l\'état vide quand conversations est vide')
  it('doit afficher app-chat-interface dans le template')

  // US3 — Conversation management
  it('doit dispatcher createConversation au click sur Nouveau')
  it('doit dispatcher setActiveConversation avec le bon id au click sur une conversation')
  it('doit dispatcher deleteConversation quand ConfirmationService retourne true')
  it('ne doit pas dispatcher deleteConversation quand ConfirmationService retourne false')

  // US4 — Sidebar toggle
  it('doit passer sidebarCollapsed à true au premier appel de toggleSidebar()')
  it('doit passer sidebarCollapsed à false au deuxième appel de toggleSidebar()')

  // Integration
  it('[INTÉGRATION] doit afficher l\'élément app-chat-interface dans le template complet')
  it('[INTÉGRATION] dispatch complet : click conversation → setActiveConversation dispatché')
}
```
