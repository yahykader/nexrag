// websocket-progress.service.spec.ts — Phase 3, User Story 4
// Vérifie connect/subscribe/disconnect via STOMP mocké

import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { NgZone } from '@angular/core';
import { WebSocketProgressService, UploadProgress } from './websocket-progress.service';

const testZone = new NgZone({ enableLongStackTrace: false });

// ─── vi.hoisted — variables accessibles dans le factory vi.mock hoistée ──

const mocks = vi.hoisted(() => ({
  activate: vi.fn(),
  deactivate: vi.fn(),
  subscribe: vi.fn(),
  state: { capturedOptions: null as any, connected: false },
}));

vi.mock('@stomp/stompjs', () => ({
  Client: class MockClient {
    constructor(options: any) {
      mocks.state.capturedOptions = options;
    }
    get connected(): boolean {
      return mocks.state.connected;
    }
    set connected(v: boolean) {
      mocks.state.connected = v;
    }
    activate(...args: any[]): any {
      return mocks.activate(...args);
    }
    deactivate(...args: any[]): any {
      return mocks.deactivate(...args);
    }
    subscribe(...args: any[]): any {
      return mocks.subscribe(...args);
    }
  },
}));

vi.mock('sockjs-client', () => ({
  default: vi.fn().mockImplementation(() => ({})),
}));

// ─── Suite ────────────────────────────────────────────────────────────────

describe('WebSocketProgressService', () => {
  let spectator: SpectatorService<WebSocketProgressService>;

  const createService = createServiceFactory({
    service: WebSocketProgressService,
    providers: [{ provide: NgZone, useValue: testZone }],
  });

  beforeEach(() => {
    mocks.state.capturedOptions = null;
    mocks.state.connected = false;
    mocks.activate.mockReset();
    mocks.deactivate.mockReset();
    mocks.subscribe.mockReset().mockReturnValue({ unsubscribe: vi.fn() });
    spectator = createService();
  });

  // helper — connecte, marque le client connected, et résout via onConnect
  async function connectService(): Promise<void> {
    const promise = spectator.service.connect();
    mocks.state.connected = true;
    mocks.state.capturedOptions?.onConnect({});
    await promise;
  }

  // ─── US4 — AC1 ────────────────────────────────────────────────────────────

  it('doit résoudre la Promise quand onConnect se déclenche', async () => {
    const connectPromise = spectator.service.connect();

    expect(mocks.activate).toHaveBeenCalled();
    mocks.state.capturedOptions.onConnect({});

    await expect(connectPromise).resolves.toBeUndefined();
  });

  // ─── US4 — AC2 ────────────────────────────────────────────────────────────

  it('doit résoudre immédiatement sans créer un nouveau client si déjà connecté', async () => {
    await connectService();

    mocks.state.connected = true;
    const activateCountBefore = mocks.activate.mock.calls.length;

    await spectator.service.connect();

    expect(mocks.activate.mock.calls.length).toBe(activateCountBefore);
  });

  // ─── US4 — AC3 ────────────────────────────────────────────────────────────

  it('doit émettre le progress et propager via progress$ quand un message STOMP arrive', async () => {
    await connectService();

    const progressEvents: UploadProgress[] = [];
    const globalEvents: UploadProgress[] = [];

    spectator.service.progress$.subscribe((p) => globalEvents.push(p));
    spectator.service.subscribeToProgress('batch-abc').subscribe((p) => progressEvents.push(p));

    const stompCallback = mocks.subscribe.mock.calls[0][1];
    const payload: UploadProgress = {
      batchId: 'batch-abc',
      filename: 'doc.pdf',
      stage: 'PROCESSING',
      progressPercentage: 50,
      message: '50% traité',
    };
    stompCallback({ body: JSON.stringify(payload) });

    expect(progressEvents).toHaveLength(1);
    expect(progressEvents[0].progressPercentage).toBe(50);
    expect(globalEvents).toHaveLength(1);
    expect(globalEvents[0].stage).toBe('PROCESSING');
  });

  // ─── US4 — AC4 ────────────────────────────────────────────────────────────

  it('doit compléter l\'observable quand le stage est COMPLETED', async () => {
    await connectService();

    let completed = false;
    spectator.service.subscribeToProgress('batch-done').subscribe({
      complete: () => (completed = true),
    });

    const stompCallback = mocks.subscribe.mock.calls[0][1];
    stompCallback({
      body: JSON.stringify({
        batchId: 'batch-done',
        filename: 'f.pdf',
        stage: 'COMPLETED',
        progressPercentage: 100,
        message: 'terminé',
      }),
    });

    expect(completed).toBe(true);
  });

  // ─── US4 — AC5 ────────────────────────────────────────────────────────────

  it('doit désabonner toutes les souscriptions et désactiver le client lors du disconnect', async () => {
    await connectService();

    const mockSub = { unsubscribe: vi.fn() };
    mocks.subscribe.mockReturnValue(mockSub);

    spectator.service.subscribeToProgress('batch-x').subscribe();
    spectator.service.disconnect();

    expect(mockSub.unsubscribe).toHaveBeenCalled();
    expect(mocks.deactivate).toHaveBeenCalled();
  });

  // ─── US4 — AC6 ────────────────────────────────────────────────────────────

  it('doit tenter le fallback SockJS quand onWebSocketError se déclenche', async () => {
    spectator.service.connect();

    expect(mocks.state.capturedOptions?.webSocketFactory).toBeUndefined();
    const activateBefore = mocks.activate.mock.calls.length;

    mocks.state.capturedOptions.onWebSocketError(new Event('error'));

    // Attendre que l'import dynamique de sockjs-client et le second activate se déclenchent
    await vi.waitFor(
      () => {
        expect(mocks.activate.mock.calls.length).toBeGreaterThan(activateBefore);
      },
      { timeout: 2000 },
    );
  });
});
