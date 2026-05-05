# Research: PHASE 10 — Ingestion Components Test Suite

**Branch**: `017-ingestion-components-tests` | **Date**: 2026-05-04

## R-001 — Spectator with Angular 21 Standalone Components

**Decision**: Use `createComponentFactory({ component: X, imports: [StandaloneChild, ...] })` — the `imports` array replaces `declarations` for standalone components.

**Rationale**: Angular 21 is fully standalone (no `NgModule` except `MaterialModule`). Spectator's `createComponentFactory` detects standalone components automatically when `imports` is provided. All 8 Phase 10 components are `standalone: true`.

**Alternatives considered**:
- `declarations: [X]` — rejected; valid only for NgModule-based components (not applicable).
- Direct `TestBed.configureTestingModule` — rejected; forbidden by Constitution Principle VI.

**Key pattern**:
```ts
const createComponent = createComponentFactory({
  component: UploadZoneComponent,
  imports: [CommonModule],
  providers: [provideMockStore({ initialState })]
});
```

---

## R-002 — `@ViewChild` Modal Testing: Real Child Import

**Decision**: Import real child modal (`DeleteAllModalComponent`, `DeleteBatchModalComponent`) in the parent spec's `imports` array. Access via `spectator.component.modal` (the `@ViewChild` ref) or `spectator.query(ChildComponent)`.

**Rationale**: `@ViewChild` wiring only resolves when the child is actually rendered in the same component tree. Mocking the child would leave the `@ViewChild` reference `undefined`, making it impossible to test `modal.openModal()` calls. Spec clarification Q4 confirmed real-child approach.

**Pattern**:
```ts
const createComponent = createComponentFactory({
  component: DeleteAllButtonComponent,
  imports: [CommonModule, DeleteAllModalComponent],
  providers: [
    provideMockStore({ initialState }),
    mockProvider(NotificationService)
  ]
});

it('...', () => {
  spectator.click('.btn-delete-all');
  expect(spectator.component.modal).toBeTruthy();
});
```

**Alternatives considered**:
- `createSpyObject(DeleteAllModalComponent)` provided as a spy — rejected; `@ViewChild` queries the rendered DOM, not the DI tree.
- Testing modal trigger at Phase 11 (upload page) — rejected by spec Q4.

---

## R-003 — Bootstrap JS Modal in Tests

**Decision**: Mock `window.bootstrap` as a minimal stub before each test that triggers `openModal()` / `closeModal()`. Test business logic (state changes, event emissions) rather than DOM modal visibility.

**Rationale**: `DeleteAllModalComponent.openModal()` and `DeleteBatchModalComponent.openModal()` call `(window as any).bootstrap.Modal`. In jsdom (Vitest's browser emulator), `window.bootstrap` is undefined, causing a `TypeError`. The modal show/hide is a Bootstrap JS concern — unit tests should verify the component's state transitions and event outputs, not Bootstrap DOM side-effects.

**Pattern** (in `beforeEach` or globally):
```ts
(window as any).bootstrap = {
  Modal: class {
    constructor(_el: HTMLElement) {}
    show() {}
    hide() {}
    static getInstance(_el: HTMLElement) { return null; }
  }
};
```

**What to test instead of DOM modal visibility**:
- `DeleteAllModalComponent`: verify `step`, `confirmationText`, `isDeleting` state after calling `reset()` and `validateAndDelete()`.
- `DeleteBatchModalComponent`: verify `isDeleting` state transitions and event emissions.

**Alternatives considered**:
- Mock `document.getElementById` — rejected; too invasive and brittle across all DOM lookups in the component.
- Skip `openModal` tests entirely — rejected; modal state reset on `openModal()` is a business logic concern.

---

## R-004 — `DeleteBatchModalComponent.confirmed` Event Contract

**Decision**: `confirmed` is `EventEmitter<void>`. Tests verify: (1) the `@Input() batchId` was set to the expected value before opening, and (2) `confirmed.emit()` fires when confirm is clicked. The batchId is NOT carried in the event payload.

**Rationale**: Component source: `@Output() confirmed = new EventEmitter<void>()`. The parent (`UploadItemComponent`) reads `this.upload.existingBatchId || this.upload.batchId` and dispatches `deleteBatch({ batchId })` after receiving the event. The batchId check is a parent-level concern.

**Impact on spec**: The test plan entry "doit émettre confirmed avec le batchId" means "when `batchId` input is set correctly AND the user confirms, then `confirmed` emits". The assertion is on the `@Input()` value and the event firing, not on the event payload.

**Test pattern**:
```ts
spectator.setInput('batchId', 'batch-123');
spectator.setInput('filename', 'doc.pdf');
spectator.click('.btn-confirm');
expect(confirmedSpy).toHaveBeenCalledOnce();
// batchId correctness verified by @Input assertion above
```

---

## R-005 — `DeleteAllButtonComponent`: Upload List Empty Guard (Production Change)

**Decision**: Add `selectAllUploads` (from ingestion store) to `DeleteAllButtonComponent` to compute a second disabled condition alongside the existing `selectCrudLoading` check.

**Rationale**: Spec Q3 clarification confirmed that the button must be disabled when the upload list is empty. Current code only reads `selectCrudLoading`. This gap leaves a usability issue where users can trigger Delete All on an empty system. The change is a 3-line addition to the component: inject the selector, combine with `combineLatest`, bind to the template.

**Minimal change**:
```ts
// In constructor:
this.isDisabled$ = combineLatest([
  this.store.select(selectCrudLoading),
  this.store.select(selectAllUploads)
]).pipe(map(([loading, uploads]) => loading || uploads.length === 0));
```

**Template binding**: `[disabled]="isDisabled$ | async"`

**Alternatives considered**:
- Leave the guard to the parent page (upload-page) via `@Input() disabled` — rejected by spec Q3 (component should be self-guarding).
- Test only the `isDeleting` condition — rejected; Q3 explicitly confirmed both conditions.

---

## R-006 — `RateLimitIndicatorComponent` Countdown Test Strategy

**Decision**: Use mock store state changes (via `mockStore.setState`) to simulate countdown values. Do NOT use `fakeAsync` / `tick` for the component spec — the countdown is driven entirely by the store's `decrementCountdown` effect (Phase 9), not by the component itself.

**Rationale**: `RateLimitIndicatorComponent` reads `remaining$` and `percentage$` from selectors — it is a pure display component. The "updates every second" behaviour is an effect responsibility (tested in Phase 9). The component test verifies template reactivity to store emissions.

**Pattern**:
```ts
it('doit mettre à jour le compteur quand le store émet', () => {
  mockStore.overrideSelector(selectUploadRemaining, 25);
  mockStore.refreshState();
  spectator.detectChanges();
  expect(spectator.query('.countdown')).toHaveText('25');
});
```

**For `RateLimitToastComponent`**: Same pattern — `isRateLimited$`, `retryAfterSeconds$`, and `message$` all come from the store. The "disappears after delay" test uses `overrideSelector` to toggle `isRateLimited` from `true` to `false` and verifies the template hides via `*ngIf`.

**Alternatives considered**:
- `fakeAsync(() => { tick(1000); })` — rejected; no timer logic exists in the component classes themselves.
- Real store with `decrementCountdown` action dispatched in test — rejected; would require real effects (forbidden by Principle X).
