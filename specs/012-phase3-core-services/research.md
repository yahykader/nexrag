# Research: Phase 3 — Core Services Test Suite

**Branch**: `012-phase3-core-services`
**Date**: 2026-04-30
**Input**: `spec.md` + service source inspection + Phase 2 research baseline

---

## Decision 1 — Test runner integration

**Decision**: Unchanged from Phase 2 — `@angular/build:unit-test` builder with Vitest globals.

**Rationale**: `angular.json` already has `"test": { "builder": "@angular/build:unit-test" }`.
`tsconfig.spec.json` already has `"types": ["vitest/globals"]`. All globals (`describe`, `it`,
`beforeEach`, `vi`, `expect`) are available without imports.

**Alternatives considered**: None — same as Phase 2 (already resolved).

---

## Decision 2 — Spectator factory selection per service type

**Decision**: Use `createHttpFactory<ServiceName>` for services that directly inject `HttpClient`
(`CrudApiService`, `IngestionApiService`, `VoiceService`). Use `createServiceFactory` for
services without `HttpClient` (`NotificationService`, `WebSocketProgressService`).
`StreamingApiService` uses `createServiceFactory` with manual `provideHttpClient()` +
`provideHttpClientTesting()` because it mixes `EventSource` (not via HttpClient) with two
HttpClient methods (`cancelStream`, `healthCheck`).

**Rationale**: Unlike Phase 2 where `createHttpFactory` conflicted with `withInterceptors`,
Phase 3 service tests do not register interceptors — the `createHttpFactory` helper
(`HttpClientTestingModule` internally) is safe here. However, `StreamingApiService` has a
split identity: its main `stream()` method bypasses `HttpClient` entirely (uses raw
`EventSource`), while `cancelStream()` and `healthCheck()` use `this.http`. The cleanest
approach is `createServiceFactory` + functional providers so all three methods can be tested
from the same spec harness.

**`HttpTestingController` retrieval** (same as Phase 2):
```ts
let controller: HttpTestingController;
beforeEach(() => {
  spectator = createService();
  controller = spectator.inject(HttpTestingController);
});
afterEach(() => controller.verify());
```

**Alternatives considered**:
- `createServiceFactory` + `provideHttpClient()` + `provideHttpClientTesting()` for all six —
  more verbose but fully uniform. Rejected in favour of the simpler `createHttpFactory` where
  no interceptors are involved, to reduce boilerplate.

---

## Decision 3 — EventSource mocking strategy (`StreamingApiService`)

**Decision**: Use a `MockEventSource` class registered via `vi.stubGlobal('EventSource', MockEventSource)`
inside `beforeEach`, restored via `vi.unstubAllGlobals()` in `afterEach`.

**Rationale**: jsdom (Vitest's DOM environment) does not ship a real `EventSource`
implementation. The service constructor calls `new EventSource(url)` — stubbing the global
class allows tests to capture the instance, fire named events, and trigger `onerror` without
a network connection.

**Minimal `MockEventSource` shape**:
```ts
class MockEventSource {
  static lastInstance: MockEventSource | null = null;
  private handlers: Record<string, Array<(e: MessageEvent) => void>> = {};
  onerror: ((e: Event) => void) | null = null;
  readyState: number = 1; // OPEN

  constructor(public url: string) {
    MockEventSource.lastInstance = this;
  }

  addEventListener(type: string, handler: (e: MessageEvent) => void) {
    (this.handlers[type] ??= []).push(handler);
  }

  /** Helper: fire a named SSE event with a JSON payload */
  emit(type: string, data: object) {
    const event = new MessageEvent(type, { data: JSON.stringify(data) });
    (this.handlers[type] ?? []).forEach(h => h(event));
  }

  close() { this.readyState = 2; } // CLOSED
}
```

**Stub lifecycle**:
```ts
beforeEach(() => vi.stubGlobal('EventSource', MockEventSource));
afterEach(() => vi.unstubAllGlobals());
```

**Alternatives considered**:
- WireMock / MSW (Mock Service Worker) — overkill for unit tests; requires a network layer.
- Manual `EventSource` polyfill from npm — adds a dependency; MockEventSource above is
  sufficient for this test scope.

---

## Decision 4 — STOMP Client mocking (`WebSocketProgressService`)

**Decision**: Provide a hand-rolled stub object via `mockProvider(Client, mockStompClient)`
where `mockStompClient` is a plain object with `vi.fn()` stubs for each used method.

**Rationale**: `@stomp/stompjs` `Client` is instantiated inside `connect()` via
`new Client({ ... })`. Since the service constructs the client internally (not via DI), the
cleanest isolation is to spy on/replace the `Client` constructor via
`vi.mock('@stomp/stompjs', () => ({ Client: vi.fn().mockImplementation(() => mockStompClient) }))`.

**Minimal mock shape**:
```ts
const mockStompClient = {
  connected: false,
  activate: vi.fn(),
  deactivate: vi.fn(),
  subscribe: vi.fn().mockReturnValue({ unsubscribe: vi.fn() }),
  onConnect: null as ((frame: any) => void) | null,
  onStompError: null as ((frame: any) => void) | null,
  onWebSocketError: null as ((event: any) => void) | null,
};
```

Tests simulate connection by directly calling:
```ts
mockStompClient.onConnect?.({ command: 'CONNECTED', headers: {}, body: '' });
```

**`subscribeToProgress` message simulation**:
```ts
const messageCallback = mockStompClient.subscribe.mock.calls[0][1];
messageCallback({ body: JSON.stringify(progressPayload) });
```

**Alternatives considered**:
- `SpyObject<Client>` via Spectator `mockProvider` — `Client` has many methods not used by
  the service; the minimal hand-rolled stub is cleaner and avoids auto-spying on 30+ methods.
- Real STOMP + in-process broker — integration concern, out of scope for Phase 3 unit tests.

---

## Decision 5 — MediaRecorder / getUserMedia mocking (`VoiceService`)

**Decision**: Stub both `navigator.mediaDevices.getUserMedia` and the `MediaRecorder`
constructor globally using `vi.stubGlobal`.

**Rationale**: jsdom does not implement `MediaRecorder` or `getUserMedia`. Both must be stubbed.
`getUserMedia` is mocked to return a resolved `Promise<MediaStream>` so `startRecording()`'s
`async/await` path completes. `MediaRecorder` is replaced with a class whose `start()` sets
state to `'recording'`, `stop()` calls `onstop`, and `ondataavailable` can be triggered by tests.

**Mock shapes**:
```ts
// Minimal MediaStream mock
const mockStream = {
  getTracks: () => [{ stop: vi.fn() }],
} as unknown as MediaStream;

// MockMediaRecorder
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

  start(_timeslice?: number) { this.state = 'recording'; }
  stop()  { this.state = 'inactive'; this.onstop?.(); }
}
```

**getUserMedia stub**:
```ts
beforeEach(() => {
  vi.stubGlobal('navigator', {
    mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(mockStream) },
  });
  vi.stubGlobal('MediaRecorder', MockMediaRecorder);
});
afterEach(() => vi.unstubAllGlobals());
```

**`isRecordingSupported()` false branch**:
```ts
vi.stubGlobal('navigator', { mediaDevices: undefined });
expect(service.isRecordingSupported()).toBe(false);
```

**Alternatives considered**:
- `vi.spyOn(navigator.mediaDevices, 'getUserMedia')` — requires `mediaDevices` to already exist
  in jsdom (it does not); full `navigator` stub needed.
- MediaRecorder polyfill npm package — unnecessary; hand-rolled mock above covers all test paths.

---

## Decision 6 — NotificationService: Toast ID format update (production change)

**Decision**: Update `NotificationService.show()` to generate IDs as
`Date.now() + '-' + (++counter)` where `counter` is a module-level `let counter = 0`.

**Rationale**: Clarification Q3 mandated the `"<timestamp>-<counter>"` format to guarantee
uniqueness in synchronous same-millisecond calls. The current `Date.now().toString()` is flaky
under Vitest's jsdom environment where two synchronous calls resolve to the same millisecond.

**Minimal change to `notification.service.ts`**:
```ts
// Before:
const toast: Toast = { id: Date.now().toString(), ... };

// After:
let _toastCounter = 0; // module-level (outside class)
// inside show():
const toast: Toast = { id: `${Date.now()}-${++_toastCounter}`, ... };
```

**Test assertion pattern**:
```ts
const ids: string[] = [];
service.toasts$.subscribe(t => ids.push(t.id));
service.success('A', 'a');
service.success('B', 'b');
expect(ids[0]).not.toBe(ids[1]);
expect(ids[0]).toMatch(/^\d+-\d+$/); // timestamp-counter format
```

---

## Decision 7 — VoiceService: no-op guard (production change)

**Decision**: Add an early-return guard to `startRecording()`:
`if (this.isRecording()) return;`

**Rationale**: Clarification Q1 mandated that calling `startRecording()` while already
recording must be a no-op. The guard prevents a second `getUserMedia` call and ensures
`MockMediaRecorder.lastInstance` doesn't get replaced mid-recording.

**Minimal change to `voice.service.ts`**:
```ts
async startRecording(): Promise<void> {
  if (this.isRecording()) return;  // ← ADD THIS LINE
  // ... rest of existing implementation
}
```

---

## Decision 8 — NgZone injection for WebSocketProgressService

**Decision**: Inject `NgZone` via `createServiceFactory` providers and pass it to the service.
Assert only on emitted *values* (not on whether `zone.run()` was called) — zone correctness
is deferred to Phase 7/10 per Clarification Q5.

**Rationale**: `WebSocketProgressService` constructor receives `NgZone` via Angular DI.
Spectator's `createServiceFactory` provides a real `NgZone` by default in the test environment.
No spy setup needed — STOMP callbacks are simulated synchronously in tests, so zone wrapping
doesn't affect test timing.

```ts
const createService = createServiceFactory({
  service: WebSocketProgressService,
  // NgZone is provided automatically by Spectator
});
```

---

## Summary Table

| Unknown | Decision | Key Constraint |
|---------|----------|----------------|
| Factory for HTTP services | `createHttpFactory` for CrudApi, IngestionApi, Voice | No interceptors → no conflict |
| Factory for StreamingApiService | `createServiceFactory` + `provideHttpClient` + `provideHttpClientTesting` | Mixed EventSource + HttpClient |
| Factory for non-HTTP services | `createServiceFactory` | NotificationService, WebSocketProgressService |
| EventSource mocking | `vi.stubGlobal('EventSource', MockEventSource)` | jsdom has no native EventSource |
| STOMP Client mocking | `vi.mock('@stomp/stompjs', MockClient)` | Client constructed internally in service |
| MediaRecorder / getUserMedia | `vi.stubGlobal` for both | jsdom has no native MediaRecorder |
| Toast ID uniqueness | Module counter `Date.now() + '-' + counter` | Service change required |
| startRecording no-op | `if (this.isRecording()) return;` guard | Service change required |
| NgZone in WebSocket tests | Real NgZone injected by Spectator | Zone correctness deferred to Phase 7/10 |
