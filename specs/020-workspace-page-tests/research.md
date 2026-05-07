# Research: Workspace Page Integration Tests

**Feature**: `020-workspace-page-tests`  
**Date**: 2026-05-07  
**Status**: Complete — all unknowns resolved via codebase inspection

---

## Decision 1: Stub Component Pattern

**Decision**: Use explicit `@Component` stub classes via `overrideComponents` in `createComponentFactory`.

**Rationale**: The `chat-page.component.spec.ts` (Phase 8) establishes this exact pattern:
```ts
@Component({ selector: 'app-chat-interface', template: '', standalone: true })
class ChatInterfaceStub {}

createComponentFactory({
  overrideComponents: [[
    ChatPageComponent,
    { remove: { imports: [ChatInterfaceComponent] }, add: { imports: [ChatInterfaceStub] } }
  ]],
})
```
This pattern preserves selector-typo detection (a broken selector surfaces as an Angular template error), unlike `NO_ERRORS_SCHEMA` which silently suppresses all unknown elements.

**Alternatives considered**:
- `NO_ERRORS_SCHEMA`: Rejected — prohibited by FR-009 (spec clarification Q2); hides selector regressions.
- `ng-mocks` `MockComponent`: Rejected — adds a new dependency not present in any other phase.

**Applied to workspace**: Three stubs needed — `UploadPageStub`, `ChatPageStub`, `ToastContainerStub`.

---

## Decision 2: ToastContainerComponent Production Change

**Decision**: Add `<app-toast-container>` to `workspace.component.html` and import `ToastContainerComponent` in `workspace.component.ts` as part of this phase.

**Rationale**: Confirmed in-scope by spec clarification Q1. The `ToastContainerComponent`:
- Selector: `app-toast-container`
- Location: `src/app/shared/components/toast-container/toast-container.component.ts`
- Dependencies: `NotificationService` (injected via constructor)
- Standalone: `true`

In unit tests, `ToastContainerComponent` will be replaced with a stub (`ToastContainerStub`). In integration tests, the real `ToastContainerComponent` is imported directly alongside a mocked `NotificationService`.

**Alternatives considered**:
- Deferring the template change to a separate task: Rejected — leaves FR-006 untestable.

---

## Decision 3: CSS Class Query Strategy

**Decision**: Query layout regions by CSS class (`.workspace-sidebar`, `.workspace-main`) using `spectator.query('.workspace-sidebar')`.

**Rationale**: Confirmed in spec clarification Q5. CSS class names are more stable than element tags — a refactor from `<aside>` to `<div>` (accessibility improvement) would not break the test. The classes `.workspace-sidebar` and `.workspace-main` are defined in `workspace.component.scss` and serve as the layout contract.

**Alternatives considered**:
- Element tag queries (`aside`, `main`): Rejected — breaks on tag refactors.
- Both class and tag: Rejected — over-specification; class alone is sufficient.

---

## Decision 4: Mock Store Composition for Integration Tests

**Decision**: Seed all five store slices (`ingestion`, `progress`, `rateLimit`, `crud`, `chat`) using existing helpers.

**Rationale**: Confirmed in spec clarification Q4. The project already has ready-made helpers:
- `mockFullIngestionState()` from `src/app/features/ingestion/components/testing/ingestion-test.helpers.ts` → provides `ingestion`, `progress`, `rateLimit`, `crud` slices
- `buildChatState()` from `src/app/test-helpers.ts` → provides the `chat` slice

Full integration state:
```ts
provideMockStore({
  initialState: {
    ...mockFullIngestionState(),
    chat: buildChatState(),
  },
})
```

**Alternatives considered**:
- Minimal (ingestion + chat only): Rejected — rate-limit integration scenario requires `rateLimit` slice.
- Per-test seeding: Rejected — adds boilerplate without improving precision for a shell with no store selectors of its own.

---

## Decision 5: Integration Test Scenarios

**Decision**: Three integration tests, all using a separate `describe('[INTÉGRATION]')` block with real `ToastContainerComponent` and mock store:

1. **Toast integration**: Use real `ToastContainerComponent` (not stub) + mocked `NotificationService` → trigger `success()` → verify `.toast` element appears in DOM.
2. **Full layout with real toast**: Verify that when the workspace is rendered with real `ToastContainerComponent`, all three child selectors (`app-upload-page`, `app-chat-page`, `app-toast-container`) are present in the DOM simultaneously.
3. **Store dispatch propagation**: Dispatch `rateLimitExceeded` action → verify the mock store reflects the new state via `store.setState()` + `spectator.detectChanges()`.

**Rationale**: `WorkspaceComponent` has no `ngOnInit`, no subscriptions, and no store selectors — it is a pure composition shell. Integration tests therefore focus on the one genuine integration point: the `ToastContainerComponent` receiving real notifications. Pulling in real `UploadPageComponent` and `ChatPageComponent` for integration tests would require their full transitive provider trees, adding complexity disproportionate to value (each is already thoroughly tested in its own phase).

**Alternatives considered**:
- Full real child pages in integration tests: Rejected — excessive transitive providers; child behavior already covered in Phases 7–11.
- Subscription leak test: Not applicable — `WorkspaceComponent` has no subscriptions to leak.

---

## Resolved Unknowns Summary

| Unknown | Resolution |
|---|---|
| Stub pattern | `overrideComponents` with explicit `@Component` stubs |
| ToastContainer addition | In-scope; template + import added in this phase |
| Query strategy | CSS class names only |
| Mock store slices | All five via `mockFullIngestionState()` + `buildChatState()` |
| Integration scenarios | Toast DOM integration + multi-child presence + store dispatch |
