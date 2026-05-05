# Quickstart: PHASE 10 — Ingestion Components Tests

**Branch**: `017-ingestion-components-tests` | **Date**: 2026-05-04

## Prerequisites

All dependencies are already installed. Verify with:

```bash
cd agentic-rag-ui
npm list @ngneat/spectator @ngrx/store vitest
```

Expected: `@ngneat/spectator@22.x`, `@ngrx/store@21.x`, `vitest@4.x`.

---

## Run Phase 10 Tests Only

```bash
cd agentic-rag-ui
npm test -- --include="**/features/ingestion/components/**"
```

---

## Run a Single Spec File

```bash
npm test -- --include="**/upload-zone.component.spec.ts"
npm test -- --include="**/upload-item.component.spec.ts"
npm test -- --include="**/progress-panel.component.spec.ts"
npm test -- --include="**/delete-all-button.component.spec.ts"
npm test -- --include="**/delete-all-modal.component.spec.ts"
npm test -- --include="**/delete-batch-modal.component.spec.ts"
npm test -- --include="**/rate-limit-indicator.component.spec.ts"
npm test -- --include="**/rate-limit-toast.component.spec.ts"
```

---

## Run with Coverage

```bash
npm test -- --include="**/features/ingestion/components/**" --coverage
```

Expected coverage thresholds (Constitution Principle IX):

| Metric | Minimum |
|--------|---------|
| Statements | ≥ 80 % |
| Branches | ≥ 75 % |
| Functions | ≥ 85 % |
| Lines | ≥ 80 % |

---

## Run All Ingestion Tests (Phases 9 + 10)

```bash
npm test -- --include="**/features/ingestion/**"
```

---

## Run Full Test Suite

```bash
npm test
```

Must complete in under 2 minutes (Constitution Principle IX).

---

## Key Implementation Notes

### Bootstrap Modal Stub (required in modal specs)

`DeleteAllModalComponent` and `DeleteBatchModalComponent` call `window.bootstrap.Modal`. Add this stub in `beforeEach` of any spec that calls `openModal()`:

```ts
beforeEach(() => {
  (window as any).bootstrap = {
    Modal: class {
      constructor(_el: HTMLElement) {}
      show() {}
      hide() {}
      static getInstance(_el: HTMLElement) { return null; }
    }
  };
});
```

### Standalone Component Imports

All components are `standalone: true`. Use `imports` (not `declarations`) in `createComponentFactory`:

```ts
const createComponent = createComponentFactory({
  component: DeleteAllButtonComponent,
  imports: [CommonModule, DeleteAllModalComponent],  // real child for @ViewChild
  providers: [
    provideMockStore({ initialState: { crud: mockCrudState(), ingestion: { uploads: [] } } }),
    mockProvider(NotificationService)
  ]
});
```

### `@ViewChild` Access in Spectator

```ts
const spectator = createComponent();
const modalRef = spectator.component.modal;  // access @ViewChild directly
expect(modalRef).toBeTruthy();
```

### Mock Store Selector Override

For components reading specific selector values:

```ts
const mockStore = spectator.inject(MockStore);
mockStore.overrideSelector(selectIsRateLimited, true);
mockStore.overrideSelector(selectRetryAfterSeconds, 30);
mockStore.refreshState();
spectator.detectChanges();
```

### Verifying Dispatched Actions

```ts
const mockStore = spectator.inject(MockStore);
const dispatchSpy = vi.spyOn(mockStore, 'dispatch');

spectator.click('.btn-delete-all');
expect(dispatchSpy).toHaveBeenCalledWith(
  CrudActions.deleteAllFiles({ confirmation: 'DELETE_ALL_FILES' })
);
```
