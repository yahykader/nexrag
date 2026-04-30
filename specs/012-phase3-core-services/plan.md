# Implementation Plan: Phase 3 — Core Services Test Suite

**Branch**: `012-phase3-core-services` | **Date**: 2026-04-30 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/012-phase3-core-services/spec.md`

---

## Summary

Write six co-located Vitest spec files for the NexRAG frontend's core services layer, covering
all external communication contracts: HTTP CRUD (`crud-api.service.spec.ts`, 8 cases),
FormData upload and ingestion (`ingestion-api.service.spec.ts`, 7 cases), SSE streaming with
citation stripping and `onerror` handling (`streaming-api.service.spec.ts`, 7 cases), STOMP
WebSocket progress tracking (`websocket-progress.service.spec.ts`, 6 cases), in-memory toast
bus with deterministic IDs (`notification.service.spec.ts`, 5 cases), and MediaRecorder-based
voice recording with Whisper transcription (`voice.service.spec.ts`, 6 cases).

Two minimal production code changes are required before writing tests:
`NotificationService` receives a counter-based unique ID scheme; `VoiceService` receives a
no-op guard on `startRecording()` when already recording.
Total: 6 spec files, 39 test cases. Target: ≥ 80 % statement, ≥ 75 % branch, ≥ 85 % function
coverage per service file.

---

## Technical Context

**Language/Version**: TypeScript 5.9 / Angular 21
**Primary Dependencies**: `@ngneat/spectator ^22.1.0`, `@angular/common/http`,
  `@stomp/stompjs`, `@ngrx/store/testing ^21.0.1`, Vitest globals (already configured)
**Storage**: N/A — no persistent storage in tested services
**Testing**: `@angular/build:unit-test` + Vitest globals (already configured in
  `tsconfig.spec.json` and `angular.json`)
**Target Platform**: Browser (jsdom in Vitest)
**Project Type**: Angular 21 standalone SPA — frontend module of NexRAG platform
**Performance Goals**: Each spec file completes in under 10 seconds (SC-005)
**Constraints**: ≥ 80 % statement / ≥ 75 % branch / ≥ 85 % function coverage per file
  (Constitution Principle IX); no real HTTP, WebSocket, SSE, or microphone connections;
  `EventSource`, `MediaRecorder`, `getUserMedia` stubbed globally via `vi.stubGlobal`
**Scale/Scope**: 6 spec files, 39 test cases total

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|-----------|-------|--------|
| **VI** — Angular Test Isolation | `createHttpFactory` for HTTP services; `createServiceFactory` for others; no direct `TestBed`; `mockProvider` / `vi.mock` for all external deps | ✅ PASS |
| **VII** — SOLID in TS Tests | One spec file per service (SRP); `SpyObject`/mock stubs honour Observable return types (LSP); only declared deps injected (ISP); no `new SomeService()` — all via Angular DI or Spectator inject (DIP) | ✅ PASS |
| **VIII** — Naming Conventions | Files: `*.spec.ts` co-located; `describe` in English (service class name); `it` labels in French imperative `"doit [action] quand [condition]"` | ✅ PASS |
| **IX** — Coverage & Quality Gates | Happy path + error path + edge case for every public method; citation-stripping and onerror branches explicitly covered | ✅ PASS |
| **X** — NgRx & Real-Time Contract Testing | `EventSource` mocked at window level via `vi.stubGlobal`; STOMP `Client` mocked via `vi.mock('@stomp/stompjs')`; no real network connections | ✅ PASS |

**No violations. Plan may proceed.**

---

## Project Structure

### Documentation (this feature)

```text
specs/012-phase3-core-services/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (Angular frontend)

```text
agentic-rag-ui/src/app/core/services/
├── crud-api.service.ts                  (existing — DO NOT modify)
├── crud-api.service.spec.ts             ← CREATE: 8 test cases
├── ingestion-api.service.ts             (existing — DO NOT modify)
├── ingestion-api.service.spec.ts        ← CREATE: 7 test cases
├── notification.service.ts              ← UPDATE: Toast ID format (counter-based)
├── notification.service.spec.ts         ← CREATE: 5 test cases
├── streaming-api.service.ts             (existing — DO NOT modify)
├── streaming-api.service.spec.ts        ← CREATE: 7 test cases
├── voice.service.ts                     ← UPDATE: no-op guard in startRecording()
├── voice.service.spec.ts                ← CREATE: 6 test cases
├── websocket-progress.service.ts        (existing — DO NOT modify)
└── websocket-progress.service.spec.ts   ← CREATE: 6 test cases
```

**Structure Decision**: Co-located spec files (Angular CLI convention, Constitution
Principle VI). Two production files receive minimal changes for correctness (not testability
hacks — both changes fix real behaviour gaps identified in the clarification phase).

---

## Phase 0 — Research (complete)

All unknowns resolved. See [research.md](research.md) for full decision log.

| Unknown | Resolution |
|---------|-----------|
| Factory for HTTP services | `createHttpFactory` (no interceptor conflict in Phase 3) |
| Factory for StreamingApiService | `createServiceFactory` + `provideHttpClient` + `provideHttpClientTesting` |
| Factory for non-HTTP services | `createServiceFactory` |
| EventSource mocking | `vi.stubGlobal('EventSource', MockEventSource)` — jsdom has no native implementation |
| STOMP Client mocking | `vi.mock('@stomp/stompjs', ...)` — client constructed internally |
| MediaRecorder / getUserMedia | `vi.stubGlobal` for both — jsdom has no native implementations |
| Toast ID uniqueness | Module counter: `Date.now() + '-' + counter` — service change required |
| startRecording no-op | `if (this.isRecording()) return;` guard — service change required |
| NgZone in WebSocket tests | Real NgZone via Spectator; zone correctness deferred to Phase 7/10 |

---

## Phase 1 — Design & Contracts (complete)

See [data-model.md](data-model.md) for all entity definitions, HTTP URL table, and
FormData payload rules.

No external API contracts are exposed — all six services are internal implementation details
of the Angular SPA. No `contracts/` directory required.

---

### Production code changes (apply first)

#### `notification.service.ts` — deterministic Toast ID

```ts
// Add at module level (outside the class, after imports):
let _toastCounter = 0;

// Update the private show() method body:
private show(type: Toast['type'], title: string, message: string, duration: number): void {
  const toast: Toast = {
    id: `${Date.now()}-${++_toastCounter}`,
    type,
    title,
    message,
    duration,
  };
  this.toastSubject.next(toast);
}
```

#### `voice.service.ts` — no-op guard

```ts
async startRecording(): Promise<void> {
  if (this.isRecording()) return;  // ← add this line only
  // rest of method unchanged
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ ... });
    // ...
  }
}
```

---

### Test setup blueprints

#### `crud-api.service.spec.ts`

```ts
import { createHttpFactory, SpectatorHttp, HttpMethod } from '@ngneat/spectator/vitest';
import { CrudApiService } from './crud-api.service';

describe('CrudApiService', () => {
  let spectator: SpectatorHttp<CrudApiService>;
  const createHttp = createHttpFactory(CrudApiService);

  beforeEach(() => spectator = createHttp());

  // Pattern for each test:
  it('doit appeler DELETE /api/v1/crud/file/:id?type=text pour deleteFile', () => {
    spectator.service.deleteFile('emb-1', 'text').subscribe();
    spectator.expectOne('/api/v1/crud/file/emb-1?type=text', HttpMethod.DELETE)
      .flush({ success: true, deletedCount: 1, message: 'OK' });
  });

  // deleteTextBatch — body assertion:
  it('doit appeler DELETE avec body pour deleteTextBatch', () => {
    spectator.service.deleteTextBatch(['e1', 'e2']).subscribe();
    const req = spectator.expectOne('/api/v1/crud/files/text/batch', HttpMethod.DELETE);
    expect(req.request.body).toEqual(['e1', 'e2']);
    req.flush({ success: true, deletedCount: 2, message: 'OK' });
  });

  // checkDuplicate — FormData body:
  it('doit envoyer un FormData en POST pour checkDuplicate', () => {
    const file = new File([''], 'test.pdf', { type: 'application/pdf' });
    spectator.service.checkDuplicate(file).subscribe();
    const req = spectator.expectOne('/api/v1/crud/check-duplicate', HttpMethod.POST);
    expect(req.request.body).toBeInstanceOf(FormData);
    req.flush({ isDuplicate: false, filename: 'test.pdf', message: 'OK' });
  });
});
```

---

#### `ingestion-api.service.spec.ts`

```ts
import { createHttpFactory, SpectatorHttp, HttpMethod } from '@ngneat/spectator/vitest';
import { IngestionApiService } from './ingestion-api.service';

describe('IngestionApiService', () => {
  let spectator: SpectatorHttp<IngestionApiService>;
  const createHttp = createHttpFactory(IngestionApiService);
  beforeEach(() => spectator = createHttp());

  // batchId omission:
  it('doit envoyer un FormData sans batchId quand uploadFile est appelé sans batchId', () => {
    const file = new File(['content'], 'doc.pdf');
    spectator.service.uploadFile(file).subscribe();
    const req = spectator.expectOne('/api/v1/ingestion/upload', HttpMethod.POST);
    expect((req.request.body as FormData).has('batchId')).toBe(false);
    req.flush({ success: true, batchId: 'b1', filename: 'doc.pdf',
      fileSize: 7, textEmbeddings: 1, imageEmbeddings: 0,
      durationMs: 100, streamingUsed: false, message: 'OK', duplicate: false });
  });

  // 422 error propagation:
  it('doit propager l\'erreur observable quand le serveur répond 422', () => {
    const file = new File([''], 'bad.pdf');
    let caught = false;
    spectator.service.uploadFile(file).subscribe({ error: () => { caught = true; } });
    spectator.expectOne('/api/v1/ingestion/upload', HttpMethod.POST)
      .flush({ message: 'Unprocessable' }, { status: 422, statusText: 'Unprocessable Entity' });
    expect(caught).toBe(true);
  });
});
```

---

#### `streaming-api.service.spec.ts`

```ts
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { NgZone } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { StreamingApiService } from './streaming-api.service';

class MockEventSource {
  static lastInstance: MockEventSource | null = null;
  private handlers: Record<string, Array<(e: MessageEvent) => void>> = {};
  onerror: ((e: Event) => void) | null = null;
  readyState = 1; // OPEN

  constructor(public url: string) { MockEventSource.lastInstance = this; }

  addEventListener(type: string, handler: (e: MessageEvent) => void) {
    (this.handlers[type] ??= []).push(handler);
  }

  emit(type: string, data: object) {
    (this.handlers[type] ?? []).forEach(h =>
      h(new MessageEvent(type, { data: JSON.stringify(data) })));
  }

  close() { this.readyState = 2; }
}

describe('StreamingApiService', () => {
  let spectator: SpectatorService<StreamingApiService>;
  let controller: HttpTestingController;

  const createService = createServiceFactory({
    service: StreamingApiService,
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });

  beforeEach(() => {
    vi.stubGlobal('EventSource', MockEventSource);
    spectator = createService();
    controller = spectator.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    controller.verify();
  });

  it('doit émettre { type: "connected" } quand l\'EventSource reçoit l\'événement connected', () => {
    const events: any[] = [];
    spectator.service.stream({ query: 'hello' }).subscribe(e => events.push(e));
    MockEventSource.lastInstance!.emit('connected', { sessionId: 's1', conversationId: 'c1' });
    expect(events[0]).toEqual({ type: 'connected', sessionId: 's1', conversationId: 'c1' });
  });

  it('doit supprimer les balises <cite> du texte avant d\'émettre l\'événement token', () => {
    const events: any[] = [];
    spectator.service.stream({ query: 'q' }).subscribe(e => events.push(e));
    MockEventSource.lastInstance!.emit('token', {
      text: 'Hello <cite index="1">source</cite> world', count: 0
    });
    expect(events[0].text).toBe('Hello  world');
  });

  it('doit émettre { type: "complete" } et compléter le stream quand complete est reçu', () => {
    let completed = false;
    const events: any[] = [];
    spectator.service.stream({ query: 'q' })
      .subscribe({ next: e => events.push(e), complete: () => { completed = true; } });
    MockEventSource.lastInstance!.emit('complete', { response: { text: 'done' } });
    expect(events[0].type).toBe('complete');
    expect(completed).toBe(true);
  });

  it('doit errorer l\'observable immédiatement quand EventSource.onerror se déclenche', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    let errored = false;
    spectator.service.stream({ query: 'q' }).subscribe({ error: () => { errored = true; } });
    const es = MockEventSource.lastInstance!;
    es.readyState = 1; // OPEN — not CLOSED
    es.onerror?.(new Event('error'));
    expect(errored).toBe(true);
    expect(es.readyState).toBe(2); // close() was called
  });
});
```

---

#### `websocket-progress.service.spec.ts`

```ts
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { WebSocketProgressService, UploadProgress } from './websocket-progress.service';
import { Client } from '@stomp/stompjs';

const mockSubscription = { unsubscribe: vi.fn() };
const mockStompClient = {
  connected: false,
  activate: vi.fn(),
  deactivate: vi.fn(),
  subscribe: vi.fn().mockReturnValue(mockSubscription),
  onConnect: null as ((f: any) => void) | null,
  onStompError: null as ((f: any) => void) | null,
  onWebSocketError: null as ((e: any) => void) | null,
};

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn().mockImplementation(() => mockStompClient),
}));

describe('WebSocketProgressService', () => {
  let spectator: SpectatorService<WebSocketProgressService>;
  const createService = createServiceFactory(WebSocketProgressService);

  beforeEach(() => {
    vi.clearAllMocks();
    mockStompClient.connected = false;
    spectator = createService();
  });

  it('doit résoudre la Promise quand onConnect se déclenche', async () => {
    const connectPromise = spectator.service.connect();
    mockStompClient.onConnect?.({ command: 'CONNECTED', headers: {}, body: '' });
    await expect(connectPromise).resolves.toBeUndefined();
  });

  it('doit émettre le progress et propager via progress$ quand un message STOMP arrive', async () => {
    mockStompClient.connected = true;
    const progress: UploadProgress = {
      batchId: 'b1', filename: 'doc.pdf', stage: 'CHUNKING',
      progressPercentage: 50, message: 'Chunking...'
    };
    const emitted: UploadProgress[] = [];
    spectator.service.subscribeToProgress('b1').subscribe(p => emitted.push(p));

    // Simulate STOMP message
    const messageCallback = mockStompClient.subscribe.mock.calls[0][1];
    messageCallback({ body: JSON.stringify(progress) });

    expect(emitted[0].progressPercentage).toBe(50);
  });

  it('doit compléter l\'observable quand le stage est COMPLETED', async () => {
    mockStompClient.connected = true;
    let completed = false;
    spectator.service.subscribeToProgress('b1')
      .subscribe({ complete: () => { completed = true; } });

    const messageCallback = mockStompClient.subscribe.mock.calls[0][1];
    messageCallback({ body: JSON.stringify({
      batchId: 'b1', filename: 'doc.pdf', stage: 'COMPLETED',
      progressPercentage: 100, message: 'Done'
    }) });
    expect(completed).toBe(true);
  });
});
```

---

#### `notification.service.spec.ts`

```ts
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { NotificationService, Toast } from './notification.service';

describe('NotificationService', () => {
  let spectator: SpectatorService<NotificationService>;
  const createService = createServiceFactory(NotificationService);
  beforeEach(() => spectator = createService());

  it('doit émettre un toast de type "success" avec le bon titre et message', () => {
    const emitted: Toast[] = [];
    spectator.service.toasts$.subscribe(t => emitted.push(t));
    spectator.service.success('Titre', 'Message');
    expect(emitted[0]).toMatchObject({
      type: 'success', title: 'Titre', message: 'Message', duration: 5000
    });
  });

  it('doit générer des IDs uniques pour deux toasts créés dans le même milliseconde', () => {
    const ids: string[] = [];
    spectator.service.toasts$.subscribe(t => ids.push(t.id));
    spectator.service.success('A', 'a');
    spectator.service.success('B', 'b');
    expect(ids[0]).not.toBe(ids[1]);
    expect(ids[0]).toMatch(/^\d+-\d+$/);
  });
});
```

---

#### `voice.service.spec.ts`

```ts
import { createHttpFactory, SpectatorHttp, HttpMethod } from '@ngneat/spectator/vitest';
import { VoiceService } from './voice.service';

const mockStream = { getTracks: () => [{ stop: vi.fn() }] } as unknown as MediaStream;

class MockMediaRecorder {
  static lastInstance: MockMediaRecorder | null = null;
  state: RecordingState = 'inactive';
  stream: MediaStream;
  ondataavailable: ((e: { data: Blob }) => void) | null = null;
  onstop: (() => void) | null = null;
  onerror: ((e: Event) => void) | null = null;

  constructor(stream: MediaStream) {
    this.stream = stream;
    MockMediaRecorder.lastInstance = this;
  }

  start(_?: number) { this.state = 'recording'; }
  stop() { this.state = 'inactive'; this.onstop?.(); }
}

describe('VoiceService', () => {
  let spectator: SpectatorHttp<VoiceService>;
  const createHttp = createHttpFactory(VoiceService);

  beforeEach(() => {
    vi.stubGlobal('navigator', {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(mockStream) },
    });
    vi.stubGlobal('MediaRecorder', MockMediaRecorder);
    spectator = createHttp();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('doit retourner true quand getUserMedia est disponible', () => {
    expect(spectator.service.isRecordingSupported()).toBe(true);
  });

  it('doit retourner false quand getUserMedia est indisponible', () => {
    vi.stubGlobal('navigator', { mediaDevices: undefined });
    expect(spectator.service.isRecordingSupported()).toBe(false);
  });

  it('doit envoyer le FormData avec audio et language à POST /api/v1/voice/transcribe', () => {
    const blob = new Blob(['audio'], { type: 'audio/webm' });
    spectator.service.transcribeWithWhisper(blob, 'en').subscribe();
    const req = spectator.expectOne('/api/v1/voice/transcribe', HttpMethod.POST);
    const fd = req.request.body as FormData;
    expect(fd.get('language')).toBe('en');
    expect(fd.get('audio')).toBeInstanceOf(Blob);
    req.flush({ success: true, transcript: 'hello', language: 'en',
      audioSize: 5, filename: 'recording.webm' });
  });
});
```

---

## Implementation sequence

1. **Apply production changes** to `notification.service.ts` (ID format) and
   `voice.service.ts` (no-op guard) — these are pre-conditions for correct test assertions.

2. **`notification.service.spec.ts`** — no mocks, pure Subject observable; ideal warmup.
   Implement all 5 cases before touching HTTP services.

3. **`crud-api.service.spec.ts`** — pure HTTP contract; 8 cases using
   `createHttpFactory` + `spectator.expectOne`. No mock setup beyond the factory.

4. **`ingestion-api.service.spec.ts`** — adds FormData inspection and 422 error
   propagation on top of the CRUD pattern. 7 cases.

5. **`streaming-api.service.spec.ts`** — requires `MockEventSource` global stub.
   Implement `connected` and `token` cases first (simplest), then `complete`, then
   `error` (named), then `onerror` (native), then `unsubscribe`, then `cancelStream`.

6. **`websocket-progress.service.spec.ts`** — requires `vi.mock('@stomp/stompjs')`.
   Implement `connect` resolve first, then `subscribeToProgress`, then `COMPLETED`
   completion, then `disconnect`, then idempotent `connect`, then SockJS fallback.

7. **`voice.service.spec.ts`** — requires `MockMediaRecorder` + `getUserMedia` stubs.
   Implement `isRecordingSupported` (both branches) first, then recording lifecycle,
   then `transcribeWithWhisper`, then `getUserMedia` rejection.

8. **Coverage verification** — run:
   ```bash
   ng test --include="**/core/services/**" --code-coverage
   ```
   Confirm all six service files meet: ≥ 80 % statements, ≥ 75 % branches, ≥ 85 % functions.

---

## Complexity Tracking

> No constitution violations. No complexity tracking required.
