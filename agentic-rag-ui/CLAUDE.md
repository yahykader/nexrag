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

# Run tests (Vitest)
npm test
```

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

## Code Style

- **Standalone components** throughout — no feature NgModules (except `MaterialModule`)
- **Prettier**: `singleQuote: true`, `printWidth: 100`, Angular HTML parser for templates
- **Lazy loading**: all feature pages use `loadComponent` in `app.routes.ts`
- **Upload mode**: default is `async` (`uploadMode: 'async'` in `IngestionState`)
- **Comments**: French inline comments in stores and services

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

## Build Notes

- Builder: `@angular/build:application` (esbuild) — **not** `@angular-devkit/build-angular`
- Bundle budget: warning at 2 MB, error at 2.5 MB (initial chunk)
- `allowedCommonJsDependencies`: `sockjs-client`, `@stomp/stompjs`, `buffer`, etc. — already whitelisted, no warnings expected
- Production: swaps `environment.ts` → `environment.prod.ts` + output hashing

## Testing Status

- Only `app.spec.ts` exists (stub — no real assertions)
- Test runner: **Vitest** (not Karma/Jasmine)
- No component or service tests yet — do not assume coverage when modifying logic
