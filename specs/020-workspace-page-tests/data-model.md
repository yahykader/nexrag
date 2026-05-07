# Data Model: Workspace Page Integration Tests

**Feature**: `020-workspace-page-tests`  
**Date**: 2026-05-07

---

## Component Public API

`WorkspaceComponent` is a pure composition shell with no inputs, outputs, or injected services.

| Element | Value |
|---|---|
| Selector | `app-workspace` |
| Template | `workspace.component.html` |
| Imports (current) | `CommonModule`, `UploadPageComponent`, `ChatPageComponent` |
| Imports (after this phase) | `CommonModule`, `UploadPageComponent`, `ChatPageComponent`, `ToastContainerComponent` |
| Inputs | none |
| Outputs | none |
| Injected services | none |
| `ngOnInit` | none |
| `ngOnDestroy` | none |

---

## Template Layout Contract

The test suite treats these CSS classes as stable observable anchors:

| CSS Class | Element (current) | Role |
|---|---|---|
| `.workspace-container` | `<div>` | Outer flex container |
| `.workspace-sidebar` | `<aside>` | Left panel — hosts `<app-upload-page>` |
| `.workspace-main` | `<main>` | Right panel — hosts `<app-chat-page>` |
| *(new)* — `<app-toast-container>` | inside `.workspace-container` | Global toast outlet — position TBD in template |

Tests query by class, not element tag.

---

## Stub Components

Three minimal stubs replace real child components in unit tests:

| Stub class | Replaces | Selector matched |
|---|---|---|
| `UploadPageStub` | `UploadPageComponent` | `app-upload-page` |
| `ChatPageStub` | `ChatPageComponent` | `app-chat-page` |
| `ToastContainerStub` | `ToastContainerComponent` | `app-toast-container` |

All stubs: `standalone: true`, `template: ''`.

---

## Mock Store Shape (Integration Tests)

Full five-slice state used in integration test `provideMockStore`:

```ts
{
  ingestion: { uploads: [], activeUploads: 0, stats: {...}, strategies: [], ... },
  progress:  { connected: false, connecting: false, progressByBatch: {}, ... },
  crud:      { loading: false, activeDeleteOperations: 0, deleteOperations: [], ... },
  rateLimit: { isRateLimited: false, retryAfterSeconds: 0, remainingTokens: {...}, ... },
  chat:      { conversations: EntityState, activeConversationId: null, isStreaming: false, ... },
}
```

Built with:
```ts
{ ...mockFullIngestionState(), chat: buildChatState() }
```

Sources:
- `mockFullIngestionState` → `src/app/features/ingestion/components/testing/ingestion-test.helpers.ts`
- `buildChatState` → `src/app/test-helpers.ts`
