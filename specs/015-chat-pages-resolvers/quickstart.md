# Quickstart: Phase 8 — Chat Pages & Resolvers Test Coverage

**Branch**: `015-chat-pages-resolvers`  
**Generated**: 2026-05-04

---

## Prerequisites

All packages already installed — no `npm install` required.

```bash
# Verify test runner works (run from agentic-rag-ui/)
npm test
```

---

## Step 1 — Create ConfirmationService

Create `agentic-rag-ui/src/app/core/services/confirmation.service.ts`:

```typescript
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ConfirmationService {
  confirm(message: string): boolean {
    return window.confirm(message);
  }
}
```

---

## Step 2 — Refactor `onDeleteConversation`

In `chat-page.component.ts`, inject `ConfirmationService` and replace the `confirm()` call:

```typescript
// Add to constructor
constructor(
  private store: Store,
  private confirmationService: ConfirmationService  // NEW
) { ... }

// Replace method body
onDeleteConversation(conversationId: string, event: Event): void {
  event.stopPropagation();
  if (this.confirmationService.confirm('Supprimer cette conversation ?')) {
    this.store.dispatch(ChatActions.deleteConversation({ conversationId }));
  }
}
```

---

## Step 3 — Create `chat-page.component.spec.ts`

Co-locate beside `chat-page.component.ts`. Key setup:

```typescript
import { Component } from '@angular/core';
import { createComponentFactory, mockProvider, Spectator } from '@ngneat/spectator';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { ChatPageComponent } from './chat-page.component';
import { ChatInterfaceComponent } from '../../components/chat-interface/chat-interface.component';
import { ConfirmationService } from '../../../../core/services/confirmation.service';
import { conversationsAdapter, initialChatState, Conversation } from '../../store/chat.state';
import * as ChatActions from '../../store/chat.actions';

@Component({ selector: 'app-chat-interface', template: '', standalone: true })
class ChatInterfaceStub {}

function mockConversation(overrides: Partial<Conversation> = {}): Conversation {
  return { id: 'conv-1', title: 'Test', messages: [], createdAt: new Date(), updatedAt: new Date(), ...overrides };
}

function mockChatState(conversations: Conversation[], activeId: string | null = null) {
  return {
    ...initialChatState,
    conversations: conversationsAdapter.setAll(conversations, conversationsAdapter.getInitialState()),
    activeConversationId: activeId,
  };
}

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
  });

  beforeEach(() => {
    spectator = createComponent();
    store = spectator.inject(MockStore);
  });

  // ... 14 it() blocks (see contracts/chat-page.contract.md)
});
```

---

## Step 4 — Create `chat.resolver.spec.ts`

Co-locate beside `chat.resolver.ts`:

```typescript
import { createServiceFactory, SpectatorService } from '@ngneat/spectator';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { ChatResolver } from './chat.resolver';
import { conversationsAdapter, initialChatState } from '../store/chat.state';
import * as ChatActions from '../store/chat.actions';

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

  // 3 it() blocks (see contracts/chat-resolver.contract.md)
});
```

---

## Step 5 — Run Phase 8 Tests

```bash
# From agentic-rag-ui/
npm test -- --include="**/features/chat/pages/**" --include="**/features/chat/resolvers/**"

# With coverage
npm test -- --coverage --include="**/features/chat/pages/**" --include="**/features/chat/resolvers/**"
```

**Coverage targets** (SC-003): statements ≥ 80 %, branches ≥ 75 %, functions ≥ 85 %

---

## Commit Convention (Constitution VIII)

```
test(phase-8): add chat-page.component.spec + chat.resolver.spec — page lifecycle & resolver guard
```

Production code change (required for testability):
```
refactor(chat-page): extract window.confirm into ConfirmationService for test isolation
```
