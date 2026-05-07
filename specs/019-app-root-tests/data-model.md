# Data Model: PHASE 14 — App Root Integration Tests

**Branch**: `019-app-root-tests` | **Date**: 2026-05-06

> Phase 14 is a test-only phase. No new domain entities or persistent data structures are introduced. This document captures the test-layer data structures (mock state shapes and test fixture types) used across the 3 spec files.

---

## Test Fixtures & Mock State

### AppState (mock root for `provideMockStore`)

Used in `app.component.spec.ts` unit tests. Only the slices referenced in the AppComponent are needed; others are omitted from the mock.

```typescript
// Minimal mock state — AppComponent has no store selectors; empty object is sufficient
const mockAppState = {};

// Usage
provideMockStore({ initialState: mockAppState })
```

**Rationale**: `AppComponent` is a stateless shell — it imports `RouterOutlet`, `RouterLink`, `RouterLinkActive`, and `<app-toast-container>` but has no direct `store.select()` calls. An empty `provideMockStore({})` satisfies the DI requirement.

---

### RouteTestFixture

Used in `app.routes.spec.ts`. Describes the input/output of each routing scenario tested.

```typescript
interface RouteTestFixture {
  input: string;          // navigated URL path
  expectedPath: string;   // resolved path after redirect/load
  isRedirect: boolean;    // true if route is a redirect (not a direct load)
  componentName?: string; // target component class name (only for non-redirects)
}
```

**Test dataset**:

| Input | Expected Path | Is Redirect | Component |
|-------|-------------|-------------|-----------|
| `''` (root) | `/workspace` | true | — |
| `/workspace` | `/workspace` | false | `WorkspaceComponent` |
| `/management` | `/workspace` | true | — (inactive route) |
| `/unknown-path` | `/workspace` | true | — (wildcard) |

---

### ProviderPresenceCheck

Used in `app.config.spec.ts`. Describes the static inspection assertions against the serialised `appConfig.providers` array.

```typescript
interface ProviderPresenceCheck {
  description: string;    // human-readable description
  markers: string[];      // string identifiers expected in JSON.stringify(appConfig.providers)
}
```

**Check dataset**:

| Description | Markers |
|-------------|---------|
| Router provider | `['workspace']` |
| Store — 5 slices | `['ingestion', 'progress', 'crud', 'rateLimit', 'chat']` |
| Effects — 5 classes | `['IngestionEffects', 'ProgressEffects', 'CrudEffects', 'RateLimitEffects', 'ChatEffects']` |
| HTTP + interceptors | `['duplicateInterceptor', 'rateLimitInterceptor']` |

---

## Source Entities Under Test

These are the production artefacts exercised by the 3 spec files. No changes to their structure are required.

### AppComponent

| Property | Type | Test relevance |
|----------|------|----------------|
| `title` | `string = 'RAG Frontend'` | Internal — not exposed in template; not tested independently |
| selector | `'app-root'` | Referenced in integration test bootstrap |
| Template | navbar + `<router-outlet>` + `<app-toast-container>` | DOM assertions in US1 |

### AppRoutes (routes array)

| Route | Path | Type | Test scenario |
|-------|------|------|---------------|
| Root redirect | `''` → `/workspace` | Redirect | US2.1 |
| Workspace | `workspace` → `WorkspaceComponent` (lazy) | Lazy load | US2.2 |
| Management | `management` → `ManagementPageComponent` (commented out) | Inactive | US2.3 |
| Wildcard | `**` → `/workspace` | Redirect | US2.4 |

### AppConfig (providers array)

| Provider | Registered slices/classes | Test scenario |
|----------|--------------------------|---------------|
| `provideRouter(routes)` | top-level routes | US3.1 |
| `provideStore({...})` | ingestion, progress, crud, rateLimit, chat | US3.2 |
| `provideEffects([...])` | IngestionEffects, ProgressEffects, CrudEffects, RateLimitEffects, ChatEffects | US3.3 |
| `provideHttpClient(withInterceptors([...]))` | duplicateInterceptor, rateLimitInterceptor | US3.4 |
| `provideAnimations()` | — | Not asserted (covered by integration test) |
| `provideMarkdown()` | — | Not asserted (covered by integration test) |
| `provideStoreDevtools()` | — | Excluded from integration tests (dev-only) |
