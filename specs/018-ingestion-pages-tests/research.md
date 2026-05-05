# Research: PHASE 11 — Ingestion Upload Page Test Suite

**Branch**: `018-ingestion-pages-tests` | **Date**: 2026-05-05

## R-001 — Page-Level Spectator Setup with Real Child Components

**Decision**: Use `createComponentFactory({ component: UploadPageComponent, imports: [CommonModule, UploadZoneComponent, UploadItemComponent, ProgressPanelComponent, DeleteAllButtonComponent, ...], providers: [provideMockStore(...)] })`. All 4 child components are real standalone imports per spec clarification Q1.

**Rationale**: `UploadPageComponent` uses structural directives (`*ngFor`, `*ngIf`) to render child component instances. Real imports allow `spectator.queryAll(UploadItemComponent)` to return actual instances, and `[disabled]` bindings on `UploadZoneComponent` to be verified via `spectator.query(UploadZoneComponent)?.disabled`. Mocked children would prevent template-binding verification.

**Key pattern**:
```ts
import { createComponentFactory, Spectator, mockProvider } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';

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
```

**Alternatives considered**:
- Shallow rendering with mocked children — rejected per spec Q1; loses `[disabled]` and `*ngFor` binding verification.
- `TestBed.configureTestingModule` directly — rejected; forbidden by Constitution Principle VI.

---

## R-002 — Bootstrap JS Cascade from Child Modals

**Decision**: Stub `window.bootstrap` in a global `beforeEach` because `DeleteAllButtonComponent` (a real child import) renders `DeleteAllModalComponent` which calls `window.bootstrap.Modal` on interactions. The page spec does not test modal interactions directly but must not throw during render.

**Rationale**: `DeleteAllButtonComponent` is imported as a real standalone and renders inside the page template (the "Danger Zone" section). Its child `DeleteAllModalComponent` references `window.bootstrap`. Without the stub, any test that triggers change detection after the button is enabled will throw `TypeError: Cannot read properties of undefined (reading 'Modal')`.

**Pattern** (in `beforeEach`):
```ts
beforeEach(() => {
  (window as any).bootstrap = {
    Modal: class {
      constructor(_el: HTMLElement) {}
      show() {}
      hide() {}
      static getInstance(_el: HTMLElement) { return null; }
    },
  };
});
```

**Carryover from**: Phase 10 research R-003 — same pattern already proven in `delete-all-button.component.spec.ts`.

---

## R-003 — Lifecycle Action Dispatch Verification

**Decision**: Use `vi.spyOn(mockStore, 'dispatch')` captured before `spectator.component.ngOnInit()` is called, then assert `toHaveBeenCalledWith(ActionCreator(...))`. For `ngOnDestroy`, call `spectator.component.ngOnDestroy()` explicitly and assert the dispatch.

**Rationale**: Spectator's `createComponent()` triggers `ngOnInit` automatically during construction. To assert `connectWebSocket` and `loadStrategies` on init, capture the spy before `createComponent()` runs — or assert post-construction. For `ngOnDestroy`, call it manually since Spectator's fixture teardown does not expose a direct hook for pre-destroy assertions.

**Pattern**:
```ts
it('doit dispatcher connectWebSocket au ngOnInit', () => {
  const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
  spectator.component.ngOnInit();  // can call again; idempotent for this assertion
  expect(dispatchSpy).toHaveBeenCalledWith(ProgressActions.connectWebSocket());
});

it('doit dispatcher disconnectWebSocket au ngOnDestroy', () => {
  const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
  spectator.component.ngOnDestroy();
  expect(dispatchSpy).toHaveBeenCalledWith(ProgressActions.disconnectWebSocket());
});
```

**Alternatives considered**:
- Wrapping the full lifecycle in a custom harness — rejected; unnecessary complexity for 2 assertions.
- Asserting dispatch count on `createComponent()` — fragile; depends on init side-effects ordering.

---

## R-004 — `[disabled]` Binding Verification on `UploadZoneComponent`

**Decision**: After overriding `selectActiveUploads` / `selectIsRateLimited` and calling `mockStore.refreshState()` + `spectator.detectChanges()`, query the child via `spectator.query(UploadZoneComponent)` and assert the `disabled` input property directly.

**Rationale**: The template passes `[disabled]="((activeUploads$ | async)?.length ?? 0) > 5 || ((isRateLimited$ | async) ?? false)"`. After change detection, Angular propagates the computed value to the child's `@Input() disabled`. Querying the component instance gives the post-binding value.

**Pattern**:
```ts
it('doit passer disabled=true à UploadZoneComponent si isRateLimited', () => {
  mockStore.overrideSelector(selectIsRateLimited, true);
  mockStore.overrideSelector(selectActiveUploads, []);
  mockStore.refreshState();
  spectator.detectChanges();
  const uploadZone = spectator.query(UploadZoneComponent);
  expect(uploadZone?.disabled).toBe(true);
});
```

**Alternatives considered**:
- Checking the DOM attribute `disabled` on the `<app-upload-zone>` element — unreliable; Angular property bindings don't always reflect as HTML attributes.

---

## R-005 — Integration Test: `overrideSelector` + `refreshState()` Step Simulation

**Decision**: Use `mockStore.overrideSelector(selector, newValue)` then `mockStore.refreshState()` then `spectator.detectChanges()` to simulate state transitions between integration test steps. Confirmed by spec clarification Q4.

**Rationale**: `overrideSelector` targets only the selectors the component subscribes to without replacing the entire store state. This avoids unintended resets on unrelated slices. `refreshState()` triggers NgRx selector re-evaluation. `detectChanges()` propagates the new values through Angular's change detection.

**Pattern** (3-step integration test):
```ts
it('[INTÉGRATION] le flux complet : sélection → uploading → done', async () => {
  // Step 1: files selected → pending
  mockStore.overrideSelector(selectPendingUploads, [mockUploadFile()]);
  mockStore.overrideSelector(selectActiveUploads, []);
  mockStore.overrideSelector(selectCompletedUploads, []);
  mockStore.refreshState();
  spectator.detectChanges();
  expect(spectator.queryAll(UploadItemComponent)).toHaveLength(1);
  expect(spectator.query('.empty-state')).toBeNull();

  // Step 2: uploading
  mockStore.overrideSelector(selectPendingUploads, []);
  mockStore.overrideSelector(selectActiveUploads, [mockUploadFile({ status: 'uploading' })]);
  mockStore.refreshState();
  spectator.detectChanges();
  expect(spectator.queryAll(UploadItemComponent)).toHaveLength(1);

  // Step 3: done
  mockStore.overrideSelector(selectActiveUploads, []);
  mockStore.overrideSelector(selectCompletedUploads, [mockUploadFile({ status: 'success' })]);
  mockStore.refreshState();
  spectator.detectChanges();
  expect(spectator.queryAll(UploadItemComponent)).toHaveLength(1);
  expect(spectator.query('.empty-state')).toBeNull();
});
```

**Alternatives considered**:
- `mockStore.setState({ ... })` — rejected per spec Q4; replaces full state and risks clearing unrelated slices.

---

## R-006 — `strategies$` Selector Out of Scope

**Decision**: `strategies$` and `selectStrategies` are NOT tested in Phase 11 (spec clarification Q2). Only `IngestionActions.loadStrategies()` dispatch is asserted.

**Rationale**: The `UploadPageComponent` template contains no rendered strategy-selection UI. Testing an observable that has no corresponding DOM output would be a no-op test with false confidence. The dispatch assertion covers the init contract without requiring strategy rendering logic.

**Impact**: No test references `selectStrategies`. The `mockIngestionState` helper's `strategies: []` initial value is sufficient.

---

## R-007 — `startAllUploads()` Async Mode Only

**Decision**: Test `startAllUploads()` with `uploadMode = 'async'` only (spec clarification Q3). Assert `IngestionActions.uploadFileAsync` is dispatched. The sync path is out of Phase 11 scope.

**Rationale**: Default `uploadMode` is `'async'`. The test plan allocates 7 unit tests; testing both modes would exceed the plan count. The sync dispatch path is implicitly covered by ingestion effects tests in Phase 9.

**Pattern**:
```ts
it('doit dispatcher uploadFileAsync pour chaque fichier en attente', () => {
  const file = mockUploadFile();
  mockStore.overrideSelector(selectIsRateLimited, false);
  mockStore.overrideSelector(selectPendingUploads, [file]);
  mockStore.overrideSelector(selectUploadMode, 'async');
  mockStore.refreshState();
  spectator.detectChanges();
  const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
  spectator.component.startAllUploads();
  expect(dispatchSpy).toHaveBeenCalledWith(
    IngestionActions.uploadFileAsync({ fileId: file.id, file: file.file })
  );
});
```
