# CLAUDE.md — agentic-rag-ui (Frontend)

Angular 21 / TypeScript 5.9 frontend for the NexRAG platform.

## Commands

```bash
# Install dependencies (from this directory)
npm install

# Dev server — http://localhost:4200 (proxies /api and /ws to :8090)
npm start

# Production build → dist/agentic-rag-ui/
npm run build

# Build in watch mode (dev)
npm run watch

# Run tests (Vitest with @ngneat/spectator)
npm test

# Run tests with coverage report (text, lcov, html)
npm test -- --coverage

# Run tests in watch mode
npm test -- --watch

# Run a specific test file
npm test -- src/app/features/chat/store/chat.store.spec.ts

# Run tests matching a pattern
npm test -- --grep "ChatStore"
```

**Note**: Prettier is configured in `package.json` but there are no scripts for it yet. To format code, run `npx prettier --write .` or install a Prettier extension in your editor.

## Key Files

| File | Purpose |
|------|---------|
| `src/app/app.config.ts` | App bootstrap — store slices, effects, interceptors, providers |
| `src/app/app.routes.ts` | Lazy-loaded routes (`loadComponent`) |
| `src/environments/environment.ts` | API base URL and WebSocket endpoints |
| `proxy.conf.json` | Dev proxy: `/api` + `/ws` → `http://localhost:8090` |
| `angular.json` | Build config (esbuild-based `@angular/build:application`) |

## Source Structure (`src/app/`)

```
app.config.ts           — Standalone bootstrap (no AppModule)
app.routes.ts           — Routes: / → /workspace (lazy); management route commented out

core/
  interceptors/
    duplicate-interceptor.ts    — Handles 409 (duplicate file)
    rate-limit.interceptor.ts   — Handles 429 (rate limit exceeded)
  models/
    crud.model.ts               — Document/batch domain types
    ingestion.model.ts          — Upload/ingestion domain types
    streaming.model.ts          — Streaming event types
  services/
    crud-api.service.ts         — Document CRUD HTTP calls
    http-client.service.ts      — Base HTTP wrapper
    ingestion-api.service.ts    — Upload HTTP calls (sync + async)
    notification.service.ts     — Toast notification wrapper
    streaming-api.service.ts    — SSE via EventSource (LLM token streaming)
    voice.service.ts            — Whisper voice transcription
    websocket-api.service.ts    — STOMP WebSocket (generic)
    websocket-progress.service.ts — Ingestion progress via STOMP

features/
  chat/
    components/
      chat-interface/           — Message list display
      message-input/            — Text input + send button
      message-item/             — Single message (markdown rendering, citations)
      voice-control/            — Voice button (mic toggle)
    pages/chat-page/            — Full chat page layout
    resolvers/chat.resolver.ts  — Prefetch before route activate
    store/                      — NgRx slice: chat (see NgRx section)

  ingestion/
    components/
      upload-zone/              — Drag-and-drop file area
      upload-item/              — Per-file progress row
      progress-panel/           — Real-time WebSocket progress display
      delete-all-button/modal/  — Confirm + delete all embeddings
      delete-batch-modal/       — Confirm + delete single batch
      rate-limit-indicator/     — Rate limit status bar
      rate-limit-toast/         — Toast on 429
    pages/upload-page/          — Full upload page layout
    store/                      — NgRx slices: ingestion, crud, progress, rate-limit

material/
  material.module.ts            — Single NgModule for all Angular Material imports

pages/
  workspace/                    — Shell page (chat + upload side by side)

shared/
  components/toast-container/  — Global toast outlet
  pipes/
    highlight.pipe.ts           — Syntax highlighting (PrismJS)
    markdown.pipe.ts            — Markdown → HTML (ngx-markdown)
```

## NgRx Store Slices

| Slice | Key State |
|-------|-----------|
| `ingestion` | `uploads: UploadFile[]` — status: `pending\|uploading\|success\|error\|duplicate\|rate-limited` |
| `progress` | Real-time ingestion progress events from WebSocket |
| `crud` | Document/batch list for management UI |
| `rateLimit` | Rate limit state (remaining, retryAfterSeconds) |
| `chat` | `conversations` (EntityAdapter, sorted by `updatedAt`) + streaming state |

**Chat state details:**
- `conversations` uses `@ngrx/entity` EntityAdapter — access via `conversationsAdapter.selectAll(state.conversations)`
- Streaming: `isStreaming`, `streamingMessageId`, `streamSessionId`
- Message status: `pending → streaming → complete | error`
- `Message.citations[]` carries RAG source references

## Real-Time Communication

| Channel | Technology | Purpose |
|---------|-----------|---------|
| LLM streaming | `EventSource` (SSE) | Token-by-token response from `/api/v1/assistant/stream` |
| Ingestion progress | STOMP over SockJS | Real-time upload/processing status from `/ws` |

- SSE is opened **directly** in `StreamingApiService` (not via `HttpClient`) — Angular zone must be re-entered manually with `NgZone.run()`
- STOMP uses `@stomp/stompjs` + `sockjs-client` — `global: 'globalThis'` polyfill required (set in `angular.json`)

## HTTP Interceptors

- `duplicateInterceptor` — catches `409 Conflict`, dispatches dedup action to store
- `rateLimitInterceptor` — catches `429 Too Many Requests`, reads `Retry-After` header, updates `rateLimit` slice

## UI Libraries

| Library | Usage |
|---------|-------|
| Angular Material 21 | Forms, dialogs, progress bars, tooltips — imported via `MaterialModule` |
| Bootstrap 5.3 | Layout, grid, utilities (CSS only — no `bootstrap.bundle` JS components) |
| Bootstrap Icons 1.13 | Icon font (`bi-*` classes) |
| ngx-markdown + marked | Markdown rendering in chat messages |
| PrismJS | Code syntax highlighting in chat |
| ngx-toastr | Toast notifications |
| angular-split | Resizable panel layout in workspace |

## Code Style & Patterns

### Architecture
- **Standalone components** throughout — no feature NgModules (except `MaterialModule`)
- **Lazy loading**: all feature pages use `loadComponent` in `app.routes.ts`
- **Dependency Injection**: Use `inject()` function in modern Angular (not constructor injection)
- **NgRx**: Store slices managed via `createFeatureSelector` and `createSelector`; use `createEffect` for side effects

### Formatting & Lint
- **Prettier**: `singleQuote: true`, `printWidth: 100`, Angular HTML parser for templates configured in `package.json`
- Format code: `npx prettier --write .` or use editor extension; **no ESLint** configured (only Prettier)

### Naming & Constants
- **Upload mode**: default is `async` (`uploadMode: 'async'` in `IngestionState`)
- **Comments**: French inline comments in stores and services (preserve this for consistency)
- **Component selectors**: use `app-` prefix (e.g., `app-chat-interface`)

### Common Patterns
- **Service injection**: `private readonly service = inject(ServiceClass)`
- **Store dispatch**: `this.store.dispatch(actionName())`
- **Store selectors**: `this.store.select(selectFeature).subscribe(...)`
- **Change detection**: Use `OnPush` change detection strategy where possible (`changeDetection: ChangeDetectionStrategy.OnPush`)
- **Unsubscribe**: Use `@ngrx/store` observables (auto-unsubscribe via async pipe in templates) or `takeUntilDestroyed()` operator

## Environment & Proxy

```ts
// environment.ts (dev = prod — same values)
apiUrl: '/api'
wsProgressEndpoint: '/ws'
wsAssistantEndpoint: '/ws/assistant'
```

Proxy (`proxy.conf.json`) transparently forwards to backend — no CORS issues in dev:
- `/api/**` → `http://localhost:8090` (HTTP)
- `/ws/**` → `http://localhost:8090` (WebSocket upgrade)

**Important**: The proxy only works in development. For production, the backend and frontend must be served from the same origin, or CORS must be explicitly configured on the backend.

## Build & Development

### Build Configuration
- Builder: `@angular/build:application` (esbuild) — **not** `@angular-devkit/build-angular`
- Bundle budget: warning at 2 MB, error at 2.5 MB (initial chunk)
- `allowedCommonJsDependencies`: `sockjs-client`, `@stomp/stompjs`, `buffer`, `url-parse`, `inherits`, `events`, `eventsource`, `crypto` — already whitelisted, no warnings expected
- Production: swaps `environment.ts` → `environment.prod.ts` + output hashing + optimization

### Debugging & Development Tools
- **Browser DevTools**: Open DevTools (F12) → Console tab shows errors/warnings
- **NgRx DevTools**: `@ngrx/store-devtools` is enabled in `app.config.ts` — Actions and state tree visible in browser DevTools
- **Source Maps**: Enabled in development builds (`sourceMap: true` in `angular.json`)
- **Angular DevTools Browser Extension**: Useful for component/change detection debugging
- **Proxy Debugging**: Dev server proxies to `:8090` — if calls fail, check backend is running on `:8090` and review `proxy.conf.json`

### File Replacement for Environments
- Development: uses `src/environments/environment.ts` (default)
- Production: swaps to `src/environments/environment.prod.ts` during `ng build`
- Both should define `apiUrl` and WebSocket endpoints

## Critical Gotchas & Important Notes

### Real-Time Communication (SSE & WebSocket)
- **SSE in `StreamingApiService`**: Opened directly via `new EventSource()` (not `HttpClient`) — must manually re-enter Angular zone with `NgZone.run()`
- **WebSocket/STOMP**: Requires `global: 'globalThis'` polyfill in `angular.json` — already set, but needed for sockjs-client
- **Zone.js integration**: Both SSE and STOMP operate outside Angular's zone; wrap callbacks with `ngZone.run()` when updating state

### Interceptors
- **Order matters**: `duplicateInterceptor` must handle 409 before it propagates; `rateLimitInterceptor` watches for 429
- **Retry-After header**: Rate limit interceptor reads this; respect its value before retrying

### Change Detection
- Not configured globally as `OnPush` — consider adding `changeDetection: ChangeDetectionStrategy.OnPush` to components if they only receive inputs or dispatch actions
- Streaming messages and real-time updates may require manual change detection if not using the async pipe

### Dependencies to Preserve
- Do not remove or downgrade: `@stomp/stompjs`, `sockjs-client` (WebSocket), `prismjs` (code highlighting), `ngx-markdown` (chat messages)
- `bootstrap.bundle.min.js` is loaded in `angular.json` scripts array; do not add Bootstrap JS components separately (CSS only via Bootstrap 5.3)

## Testing

### Test Runner & Framework
- **Vitest** (not Karma/Jasmine) — configured via `@angular/build:unit-test`
- **@ngneat/spectator** — helper library for component and service testing
- **jsdom** — DOM implementation for tests

### Coverage Thresholds
Tests are configured with the following minimum coverage thresholds (via `angular.json`):
- Statements: 80%
- Branches: 75%
- Functions: 85%
- Lines: 80%

### Current Test Status
- Only `app.spec.ts` exists (stub — no real assertions)
- No component or service tests yet — do not assume coverage when modifying logic

### Writing Tests
- Use `provideMockStore()` from `@ngrx/store` for NgRx store testing
- Use `@ngneat/spectator` for component/service unit tests
- For mocked HTTP, use Angular's `HttpClientTestingModule`
- Integration tests (Testcontainers) are in the backend only (`*IntegrationSpec` classes)

## Common Development Tasks

### Adding a New Feature Component
1. Generate component: `ng generate component features/{feature}/components/{name}` (though manual creation is fine too)
2. Make it standalone: add `standalone: true` to `@Component`
3. Import dependencies in the component (RxJS, Angular Material, shared pipes, etc.)
4. Add to feature's `pages/*.ts` or lazy route
5. Use store selectors/dispatch as needed

### Adding a New Store Feature
1. Create `store/{slice}.store.ts` with `createFeatureSelector`, `createSelector`, `createAction`, `createReducer`
2. Add to `app.config.ts` via `provideStore()` with the feature reducer
3. Create `store/{slice}.effects.ts` if side effects needed; add to `provideEffects()` in `app.config.ts`
4. Inject store and dispatch/select in components

### Debugging WebSocket Issues
1. Check browser DevTools → Network tab → WS (WebSocket) connections
2. Verify STOMP subscription paths match backend (`/topic/`, `/user/queue/` etc.)
3. Check backend is running on `:8090` and STOMP endpoint is exposed at `/ws`
4. Look for "CONNECT_ERROR" in console — usually means backend didn't accept the connection

### Testing Real-Time Updates Locally
- Start backend: `./mvnw spring-boot:run` (from `/nex-rag/`)
- Start frontend: `npm start`
- Open browser at `http://localhost:4200`
- Check browser DevTools → Application → Storage → Session Storage for any cached state issues
