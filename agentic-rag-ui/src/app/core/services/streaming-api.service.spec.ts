// streaming-api.service.spec.ts — Phase 3, User Story 3
// Vérifie les événements SSE de StreamingApiService (MockEventSource global)

import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NgZone } from '@angular/core';
import { StreamingApiService, StreamEvent } from './streaming-api.service';

const testZone = new NgZone({ enableLongStackTrace: false });

// ─── Mock EventSource ──────────────────────────────────────────────────────

class MockEventSource {
  static lastInstance: MockEventSource | null = null;

  private handlers: Record<string, Array<(e: MessageEvent) => void>> = {};
  onerror: ((e: Event) => void) | null = null;
  readyState = 1; // OPEN

  constructor(public url: string) {
    MockEventSource.lastInstance = this;
  }

  addEventListener(type: string, handler: (e: MessageEvent) => void): void {
    (this.handlers[type] ??= []).push(handler);
  }

  emit(type: string, data: object): void {
    const event = new MessageEvent(type, { data: JSON.stringify(data) });
    (this.handlers[type] ?? []).forEach((h) => h(event));
  }

  close(): void {
    this.readyState = 2; // CLOSED
  }
}

// ─── Suite ────────────────────────────────────────────────────────────────

describe('StreamingApiService', () => {
  let spectator: SpectatorService<StreamingApiService>;
  let controller: HttpTestingController;

  const createService = createServiceFactory({
    service: StreamingApiService,
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NgZone, useValue: testZone },
    ],
  });

  beforeEach(() => {
    MockEventSource.lastInstance = null;
    vi.stubGlobal('EventSource', MockEventSource);
    spectator = createService();
    controller = spectator.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    controller?.verify();
  });

  // ─── US3 — AC1 ────────────────────────────────────────────────────────────

  it('doit émettre { type: "connected" } quand l\'EventSource reçoit l\'événement connected', () => {
    const events: StreamEvent[] = [];
    spectator.service.stream({ query: 'test' }).subscribe({ next: (e) => events.push(e) });

    const src = MockEventSource.lastInstance!;
    src.emit('connected', { sessionId: 'sess-1', conversationId: 'conv-1' });

    expect(events).toHaveLength(1);
    expect(events[0].type).toBe('connected');
    expect(events[0].sessionId).toBe('sess-1');
    expect(events[0].conversationId).toBe('conv-1');

    src.close();
  });

  // ─── US3 — AC2 ────────────────────────────────────────────────────────────

  it('doit supprimer les balises <cite> du texte avant d\'émettre l\'événement token', () => {
    const events: StreamEvent[] = [];
    spectator.service.stream({ query: 'test' }).subscribe({ next: (e) => events.push(e) });

    const src = MockEventSource.lastInstance!;
    src.emit('token', { text: 'Bonjour <cite index="1">source</cite> monde', count: 0 });

    expect(events).toHaveLength(1);
    expect(events[0].type).toBe('token');
    expect(events[0].text).toBe('Bonjour  monde');

    src.close();
  });

  // ─── US3 — AC3 ────────────────────────────────────────────────────────────

  it('doit émettre { type: "complete" } et compléter le stream quand complete est reçu', () => {
    const events: StreamEvent[] = [];
    let completed = false;

    spectator.service.stream({ query: 'test' }).subscribe({
      next: (e) => events.push(e),
      complete: () => (completed = true),
    });

    const src = MockEventSource.lastInstance!;
    src.emit('complete', { response: { answer: '42' }, metadata: {} });

    expect(events).toHaveLength(1);
    expect(events[0].type).toBe('complete');
    expect(events[0].response).toEqual({ answer: '42' });
    expect(completed).toBe(true);
  });

  // ─── US3 — AC4 ────────────────────────────────────────────────────────────

  it('doit émettre { type: "error" } et errorer quand l\'événement error nommé est reçu', () => {
    const events: StreamEvent[] = [];
    let capturedError: any;

    spectator.service.stream({ query: 'test' }).subscribe({
      next: (e) => events.push(e),
      error: (err) => (capturedError = err),
    });

    const src = MockEventSource.lastInstance!;
    src.emit('error', { message: 'LLM timeout', code: 'TIMEOUT' });

    expect(events).toHaveLength(1);
    expect(events[0].type).toBe('error');
    expect(events[0].error).toBe('LLM timeout');
    expect(capturedError).toBeDefined();
  });

  // ─── US3 — AC5 ────────────────────────────────────────────────────────────

  it('doit fermer l\'EventSource quand l\'abonné se désabonne avant complete', () => {
    const sub = spectator.service.stream({ query: 'test' }).subscribe();

    const src = MockEventSource.lastInstance!;
    expect(src.readyState).toBe(1);

    sub.unsubscribe();

    expect(src.readyState).toBe(2);
  });

  // ─── US3 — AC6 ────────────────────────────────────────────────────────────

  it('doit appeler POST /api/v1/assistant/stream/:id/cancel pour cancelStream', () => {
    spectator.service.cancelStream('sess-xyz').subscribe();

    const req = controller.expectOne('/api/v1/assistant/stream/sess-xyz/cancel');
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  // ─── US3 — AC7 ────────────────────────────────────────────────────────────

  it('doit errorer l\'observable immédiatement quand EventSource.onerror se déclenche', () => {
    let capturedError: any;

    spectator.service.stream({ query: 'test' }).subscribe({
      error: (err) => (capturedError = err),
    });

    const src = MockEventSource.lastInstance!;
    if (src.onerror) {
      src.onerror(new Event('error'));
    }

    expect(capturedError).toBeDefined();
  });
});
