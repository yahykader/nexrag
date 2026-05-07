# Quickstart: Workspace Page Tests

**Feature**: `020-workspace-page-tests`  
**Date**: 2026-05-07

---

## Files to Create / Modify

| Action | File |
|---|---|
| MODIFY | `src/app/pages/workspace/workspace.component.ts` |
| MODIFY | `src/app/pages/workspace/workspace.component.html` |
| CREATE | `src/app/pages/workspace/workspace.component.spec.ts` |

---

## Step 1 — Production Fix (Template Change)

**`workspace.component.ts`** — add `ToastContainerComponent` to imports:

```ts
import { ToastContainerComponent } from '../../shared/components/toast-container/toast-container.component';

@Component({
  selector: 'app-workspace',
  standalone: true,
  imports: [
    CommonModule,
    UploadPageComponent,
    ChatPageComponent,
    ToastContainerComponent,   // ← add this
  ],
  templateUrl: './workspace.component.html',
  styleUrls: ['./workspace.component.scss']
})
export class WorkspaceComponent {}
```

**`workspace.component.html`** — add the toast outlet (position: inside `.workspace-container`, after the `<main>`):

```html
<div class="workspace-container">
  <aside class="workspace-sidebar">
    <div class="sidebar-content">
      <app-upload-page></app-upload-page>
    </div>
  </aside>

  <main class="workspace-main">
    <div class="main-content">
      <app-chat-page></app-chat-page>
    </div>
  </main>

  <app-toast-container></app-toast-container>  <!-- ← add this -->
</div>
```

---

## Step 2 — Spec File Structure

**`workspace.component.spec.ts`** — skeleton:

```ts
import { Component }                         from '@angular/core';
import { createComponentFactory, mockProvider, Spectator }
                                             from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore }       from '@ngrx/store/testing';
import { afterEach, beforeEach, describe, expect, it, vi }
                                             from 'vitest';

import { WorkspaceComponent }                from './workspace.component';
import { UploadPageComponent }               from '../../features/ingestion/pages/upload-page/upload-page.component';
import { ChatPageComponent }                 from '../../features/chat/pages/chat-page/chat-page.component';
import { ToastContainerComponent }           from '../../shared/components/toast-container/toast-container.component';
import { NotificationService }               from '../../core/services/notification.service';

import { mockFullIngestionState }            from '../../features/ingestion/components/testing/ingestion-test.helpers';
import { buildChatState }                    from '../../test-helpers';

// ── Stubs ─────────────────────────────────────────────────────────────────────
@Component({ selector: 'app-upload-page',     template: '', standalone: true }) class UploadPageStub {}
@Component({ selector: 'app-chat-page',       template: '', standalone: true }) class ChatPageStub {}
@Component({ selector: 'app-toast-container', template: '', standalone: true }) class ToastContainerStub {}

const FULL_STATE = () => ({ ...mockFullIngestionState(), chat: buildChatState() });

// ── Unit tests ────────────────────────────────────────────────────────────────
describe('WorkspaceComponent', () => {
  const createComponent = createComponentFactory({
    component: WorkspaceComponent,
    overrideComponents: [
      [WorkspaceComponent, { remove: { imports: [UploadPageComponent] },     add: { imports: [UploadPageStub] } }],
      [WorkspaceComponent, { remove: { imports: [ChatPageComponent] },       add: { imports: [ChatPageStub] } }],
      [WorkspaceComponent, { remove: { imports: [ToastContainerComponent] }, add: { imports: [ToastContainerStub] } }],
    ],
    shallow: false,
    detectChanges: true,
  });

  let spectator: Spectator<WorkspaceComponent>;

  beforeEach(() => { spectator = createComponent(); });
  afterEach(() => { vi.restoreAllMocks(); });

  // 6 unit tests
  it('doit créer le composant', () => { ... });
  it('doit afficher la région sidebar (.workspace-sidebar)', () => { ... });
  it('doit afficher la région main (.workspace-main)', () => { ... });
  it('doit afficher app-upload-page dans la sidebar', () => { ... });
  it('doit afficher app-chat-page dans le main', () => { ... });
  it('doit afficher app-toast-container dans le workspace', () => { ... });
});

// ── Integration tests ─────────────────────────────────────────────────────────
describe('WorkspaceComponent [INTÉGRATION]', () => {
  const createComponent = createComponentFactory({
    component: WorkspaceComponent,
    overrideComponents: [
      [WorkspaceComponent, { remove: { imports: [UploadPageComponent] }, add: { imports: [UploadPageStub] } }],
      [WorkspaceComponent, { remove: { imports: [ChatPageComponent] },   add: { imports: [ChatPageStub] } }],
      // ToastContainerComponent NOT stubbed here — real component used
    ],
    providers: [
      provideMockStore({ initialState: FULL_STATE() }),
      mockProvider(NotificationService),
    ],
    detectChanges: true,
  });

  let spectator: Spectator<WorkspaceComponent>;
  let store: MockStore;

  beforeEach(() => {
    spectator = createComponent();
    store = spectator.inject(MockStore);
  });
  afterEach(() => { store.resetSelectors(); vi.restoreAllMocks(); });

  // 3 integration tests
  it('[INTÉGRATION] doit afficher les trois enfants simultanément', () => { ... });
  it('[INTÉGRATION] doit afficher un toast quand NotificationService émet un succès', () => { ... });
  it('[INTÉGRATION] doit mettre à jour l\'état du store après dispatch rateLimitExceeded', () => { ... });
});
```

---

## Step 3 — Run the Spec

```bash
# From agentic-rag-ui/
npm test -- --reporter=verbose --include="**/pages/workspace/**"

# Run all tests to check no regressions
npm test
```

---

## Key Import Paths

| Symbol | Path |
|---|---|
| `WorkspaceComponent` | `./workspace.component` |
| `UploadPageComponent` | `../../features/ingestion/pages/upload-page/upload-page.component` |
| `ChatPageComponent` | `../../features/chat/pages/chat-page/chat-page.component` |
| `ToastContainerComponent` | `../../shared/components/toast-container/toast-container.component` |
| `NotificationService` | `../../core/services/notification.service` |
| `mockFullIngestionState` | `../../features/ingestion/components/testing/ingestion-test.helpers` |
| `buildChatState` | `../../test-helpers` |
