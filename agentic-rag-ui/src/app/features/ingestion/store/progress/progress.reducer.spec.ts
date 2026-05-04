import { describe, it, expect } from 'vitest';
import { progressReducer } from './progress.reducer';
import { initialProgressState } from './progress.state';
import * as ProgressActions from './progress.actions';
import { mockUploadProgress } from '../../../../test-helpers';

describe('ProgressReducer', () => {
  it('doit retourner l\'état initial', () => {
    const state = progressReducer(undefined, { type: '@@INIT' });
    expect(state).toEqual(initialProgressState);
  });

  it('connectWebSocketSuccess doit passer connected à true et connecting à false', () => {
    const connecting = progressReducer(initialProgressState, ProgressActions.connectWebSocket());
    expect(connecting.connecting).toBe(true);
    const connected = progressReducer(connecting, ProgressActions.connectWebSocketSuccess());
    expect(connected.connected).toBe(true);
    expect(connected.connecting).toBe(false);
    expect(connected.error).toBeNull();
  });

  it('subscribeToProgress doit ajouter le batchId à subscribedBatches', () => {
    const state = progressReducer(initialProgressState, ProgressActions.subscribeToProgress({ batchId: 'batch-1' }));
    expect(state.subscribedBatches).toContain('batch-1');
  });

  it('progressUpdate pour un batchId souscrit doit mettre à jour progressByBatch', () => {
    const withSubscription = { ...initialProgressState, subscribedBatches: ['batch-1'] };
    const progress = mockUploadProgress({ batchId: 'batch-1', stage: 'PROCESSING', progressPercentage: 50 });
    const state = progressReducer(withSubscription, ProgressActions.progressUpdate({ progress }));
    expect(state.progressByBatch['batch-1']).toBeDefined();
    expect(state.progressByBatch['batch-1'].progressPercentage).toBe(50);
  });

  it('progressUpdate pour un batchId non souscrit doit être un no-op (FR-015)', () => {
    const progress = mockUploadProgress({ batchId: 'batch-inconnu', stage: 'PROCESSING' });
    const state = progressReducer(initialProgressState, ProgressActions.progressUpdate({ progress }));
    expect(state.progressByBatch['batch-inconnu']).toBeUndefined();
    expect(state).toStrictEqual(initialProgressState);
  });

  it('progressUpdate COMPLETED doit marquer _shouldClear à true et définir _clearAt', () => {
    const before = Date.now();
    const withSubscription = { ...initialProgressState, subscribedBatches: ['batch-1'] };
    const progress = mockUploadProgress({ batchId: 'batch-1', stage: 'COMPLETED' });
    const state = progressReducer(withSubscription, ProgressActions.progressUpdate({ progress }));
    const entry = state.progressByBatch['batch-1'];
    expect(entry._shouldClear).toBe(true);
    expect(entry._clearAt).toBeGreaterThan(before);
    expect(entry._clearAt).toBeLessThanOrEqual(Date.now() + 5001);
  });

  it('clearProgress doit supprimer uniquement le batchId cible', () => {
    const withProgress = {
      ...initialProgressState,
      progressByBatch: {
        'batch-1': mockUploadProgress({ batchId: 'batch-1' }),
        'batch-2': mockUploadProgress({ batchId: 'batch-2' })
      }
    };
    const state = progressReducer(withProgress, ProgressActions.clearProgress({ batchId: 'batch-1' }));
    expect(state.progressByBatch['batch-1']).toBeUndefined();
    expect(state.progressByBatch['batch-2']).toBeDefined();
  });

  it('clearAllProgress doit vider progressByBatch', () => {
    const withProgress = {
      ...initialProgressState,
      progressByBatch: {
        'batch-1': mockUploadProgress({ batchId: 'batch-1' }),
        'batch-2': mockUploadProgress({ batchId: 'batch-2' })
      }
    };
    const state = progressReducer(withProgress, ProgressActions.clearAllProgress());
    expect(state.progressByBatch).toEqual({});
  });

  it('connectWebSocketError doit stocker l\'erreur et passer connected à false', () => {
    const state = progressReducer(initialProgressState, ProgressActions.connectWebSocketError({ error: 'Connexion refusée' }));
    expect(state.connected).toBe(false);
    expect(state.connecting).toBe(false);
    expect(state.error).toBe('Connexion refusée');
  });
});
