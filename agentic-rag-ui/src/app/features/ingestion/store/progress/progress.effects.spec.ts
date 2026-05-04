import { createServiceFactory, SpectatorService, SpyObject } from '@ngneat/spectator/vitest';
import { Action } from '@ngrx/store';
import { provideMockActions } from '@ngrx/effects/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { ReplaySubject, of } from 'rxjs';

import { ProgressEffects } from './progress.effects';
import * as ProgressActions from './progress.actions';
import { WebSocketProgressService } from '../../../../core/services/websocket-progress.service';
import { initialProgressState } from './progress.state';
import { mockUploadProgress } from '../../../../test-helpers';

describe('ProgressEffects', () => {
  let spectator: SpectatorService<ProgressEffects>;
  let actions$: ReplaySubject<Action>;
  let wsService: SpyObject<WebSocketProgressService>;

  const createService = createServiceFactory({
    service: ProgressEffects,
    providers: [
      provideMockActions(() => actions$),
      provideMockStore({ initialState: { progress: initialProgressState } }),
    ],
    mocks: [WebSocketProgressService],
  });

  beforeEach(() => {
    actions$ = new ReplaySubject<Action>(1);
    spectator = createService();
    wsService = spectator.inject(WebSocketProgressService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('connectWebSocket$ doit dispatcher connectWebSocketSuccess quand la Promise se résout', async () => {
    wsService.connect.mockResolvedValue(undefined);

    const results: Action[] = [];
    spectator.service.connectWebSocket$.subscribe(a => results.push(a));
    actions$.next(ProgressActions.connectWebSocket());

    await new Promise(resolve => setTimeout(resolve, 0));

    expect(results).toHaveLength(1);
    expect(results[0].type).toBe('[Progress] Connect WebSocket Success');
  });

  it('connectWebSocket$ doit dispatcher connectWebSocketError quand la Promise rejette', async () => {
    wsService.connect.mockRejectedValue(new Error('Connexion refusée'));

    const results: Action[] = [];
    spectator.service.connectWebSocket$.subscribe(a => results.push(a));
    actions$.next(ProgressActions.connectWebSocket());

    await new Promise(resolve => setTimeout(resolve, 0));

    expect(results).toHaveLength(1);
    expect(results[0].type).toBe('[Progress] Connect WebSocket Error');
    expect((results[0] as any).error).toBe('Connexion refusée');
  });

  it('subscribeToProgress$ doit mapper les événements WebSocket en progressUpdate', () => {
    const progress = mockUploadProgress({ batchId: 'batch-1', stage: 'PROCESSING' });
    wsService.subscribeToProgress.mockReturnValue(of(progress));

    const results: Action[] = [];
    spectator.service.subscribeToProgress$.subscribe(a => results.push(a));
    actions$.next(ProgressActions.subscribeToProgress({ batchId: 'batch-1' }));

    expect(results).toHaveLength(1);
    expect(results[0].type).toBe('[Progress] Progress Update');
    expect((results[0] as any).progress.batchId).toBe('batch-1');
  });

  it('autoClearCompletedProgress$ doit dispatcher clearProgress après timer(5000)', () => {
    vi.useFakeTimers();
    wsService.unsubscribeFromProgress.mockReturnValue(undefined);

    const progress = mockUploadProgress({ batchId: 'batch-1', stage: 'COMPLETED' });

    const results: Action[] = [];
    spectator.service.autoClearCompletedProgress$.subscribe(a => results.push(a));
    actions$.next(ProgressActions.progressUpdate({ progress }));

    vi.advanceTimersByTime(5000);

    expect(results).toHaveLength(1);
    expect(results[0].type).toBe('[Progress] Clear Progress');
    expect((results[0] as any).batchId).toBe('batch-1');
  });

  it('disconnectWebSocket$ doit appeler wsProgress.disconnect()', () => {
    wsService.disconnect.mockReturnValue(undefined);

    spectator.service.disconnectWebSocket$.subscribe();
    actions$.next(ProgressActions.disconnectWebSocket());

    expect(wsService.disconnect).toHaveBeenCalled();
  });
});
