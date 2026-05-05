# Quickstart: PHASE 11 — Ingestion Upload Page Test Suite

**Branch**: `018-ingestion-pages-tests` | **Date**: 2026-05-05

## Prerequisites

All Phase 9 (ingestion store) and Phase 10 (ingestion components) specs already pass:

```bash
cd agentic-rag-ui
npm test -- --include="**/features/ingestion/**"
```

---

## What to Build

Create **1 new file**:

```
src/app/features/ingestion/pages/upload-page/upload-page.component.spec.ts
```

And **extend 1 existing file** with a new factory function:

```
src/app/features/ingestion/components/testing/ingestion-test.helpers.ts
  → add: mockFullIngestionState()
```

**No production code changes required.**

---

## Spec File Skeleton

```ts
// upload-page.component.spec.ts
import { CommonModule } from '@angular/common';
import { createComponentFactory, Spectator, mockProvider } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { UploadPageComponent } from './upload-page.component';
import { UploadZoneComponent } from '../../components/upload-zone/upload-zone.component';
import { UploadItemComponent } from '../../components/upload-item/upload-item.component';
import { ProgressPanelComponent } from '../../components/progress-panel/progress-panel.component';
import { DeleteAllButtonComponent } from '../../components/delete-all-button/delete-all-button.component';
import { NotificationService } from '../../../../core/services/notification.service';

import * as IngestionActions from '../../store/ingestion/ingestion.actions';
import * as ProgressActions from '../../store/progress/progress.actions';
import {
  selectPendingUploads, selectActiveUploads, selectCompletedUploads,
  selectStats, selectUploadMode,
} from '../../store/ingestion/ingestion.selectors';
import {
  selectActiveProgressCount, selectWebSocketConnected,
} from '../../store/progress/progress.selectors';
import { selectIsRateLimited, selectRetryAfterSeconds } from '../../store/rate-limit/rate-limit.selectors';
import {
  mockUploadFile, mockFullIngestionState,
} from '../../components/testing/ingestion-test.helpers';

describe('UploadPageComponent', () => {
  const createComponent = createComponentFactory({
    component: UploadPageComponent,
    imports: [
      CommonModule,
      UploadZoneComponent,
      UploadItemComponent,
      ProgressPanelComponent,
      DeleteAllButtonComponent,
    ],
    providers: [
      provideMockStore({ initialState: mockFullIngestionState() }),
      mockProvider(NotificationService),
    ],
  });

  let spectator: Spectator<UploadPageComponent>;
  let mockStore: MockStore;

  beforeEach(() => {
    (window as any).bootstrap = {
      Modal: class {
        constructor(_el: HTMLElement) {}
        show() {}
        hide() {}
        static getInstance(_el: HTMLElement) { return null; }
      },
    };
    spectator = createComponent();
    mockStore = spectator.inject(MockStore);
  });

  // ── Unit tests ─────────────────────────────────────────────────────────────

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  it('doit dispatcher connectWebSocket au ngOnInit', () => { /* ... */ });
  it('doit dispatcher loadStrategies au ngOnInit', () => { /* ... */ });
  it('doit dispatcher disconnectWebSocket au ngOnDestroy', () => { /* ... */ });
  it('doit afficher UploadZoneComponent', () => { /* ... */ });
  it('doit afficher DeleteAllButtonComponent', () => { /* ... */ });
  it('doit afficher l\'état vide quand la liste est vide', () => { /* ... */ });

  // ── Integration tests ──────────────────────────────────────────────────────

  it('[INTÉGRATION] doit dispatcher addFilesToUpload quand filesSelected est émis', () => { /* ... */ });
  it('[INTÉGRATION] doit afficher les items par groupe de statut', () => { /* ... */ });
  it('[INTÉGRATION] le flux complet : sélection → uploading → done', () => { /* ... */ });
});
```

---

## Running Phase 11 Tests

```bash
# Run Phase 11 only
npm test -- --include="**/features/ingestion/pages/**"

# Run with coverage
npm test -- --include="**/features/ingestion/pages/**" --coverage

# Run full ingestion suite (Phases 9–11)
npm test -- --include="**/features/ingestion/**"
```

---

## Key Patterns Reference

| Scenario | Pattern |
|----------|---------|
| Override one selector | `mockStore.overrideSelector(selectX, value); mockStore.refreshState(); spectator.detectChanges();` |
| Assert dispatch | `const spy = vi.spyOn(mockStore, 'dispatch'); spectator.component.method(); expect(spy).toHaveBeenCalledWith(Action(...));` |
| Query child component | `spectator.query(UploadZoneComponent)` |
| Query all instances | `spectator.queryAll(UploadItemComponent)` |
| Emit child output | `spectator.triggerEventHandler(UploadZoneComponent, 'filesSelected', [file])` |
| Assert element hidden | `expect(spectator.query('.empty-state')).toBeNull()` |

---

## Checklist Before Committing

- [ ] All 10 tests pass (`npm test -- --include="**/features/ingestion/pages/**"`)
- [ ] No `console.error` or `console.warn` emitted during test run
- [ ] Coverage ≥ 80% statements, ≥ 75% branches for `UploadPageComponent`
- [ ] No `window.bootstrap` TypeError (check `beforeEach` stub is in place)
- [ ] 3 integration tests carry `[INTÉGRATION]` prefix in their `it()` description
- [ ] `mockFullIngestionState` helper added to `ingestion-test.helpers.ts`
- [ ] Commit message format: `test(phase-11): add upload-page.component.spec — lifecycle & integration scenarios`
