import { describe, it, expect } from 'vitest';
import * as ProgressActions from './progress.actions';
import { mockUploadProgress } from '../../../../test-helpers';

describe('ProgressActions', () => {
  it('connectWebSocket doit avoir le type [Progress] Connect WebSocket', () => {
    const action = ProgressActions.connectWebSocket();
    expect(action.type).toBe('[Progress] Connect WebSocket');
  });

  it('connectWebSocketSuccess doit avoir le type [Progress] Connect WebSocket Success', () => {
    const action = ProgressActions.connectWebSocketSuccess();
    expect(action.type).toBe('[Progress] Connect WebSocket Success');
  });

  it('connectWebSocketError doit avoir le type [Progress] Connect WebSocket Error', () => {
    const action = ProgressActions.connectWebSocketError({ error: 'Connexion refusée' });
    expect(action.type).toBe('[Progress] Connect WebSocket Error');
    expect(action.error).toBe('Connexion refusée');
  });

  it('subscribeToProgress doit avoir le type [Progress] Subscribe To Progress', () => {
    const action = ProgressActions.subscribeToProgress({ batchId: 'batch-1' });
    expect(action.type).toBe('[Progress] Subscribe To Progress');
    expect(action.batchId).toBe('batch-1');
  });

  it('progressUpdate doit avoir le type [Progress] Progress Update', () => {
    const progress = mockUploadProgress({ batchId: 'batch-1', stage: 'PROCESSING' });
    const action = ProgressActions.progressUpdate({ progress });
    expect(action.type).toBe('[Progress] Progress Update');
    expect(action.progress.batchId).toBe('batch-1');
  });

  it('clearProgress doit avoir le type [Progress] Clear Progress', () => {
    const action = ProgressActions.clearProgress({ batchId: 'batch-1' });
    expect(action.type).toBe('[Progress] Clear Progress');
    expect(action.batchId).toBe('batch-1');
  });

  it('clearAllProgress doit avoir le type [Progress] Clear All Progress', () => {
    const action = ProgressActions.clearAllProgress();
    expect(action.type).toBe('[Progress] Clear All Progress');
  });

  it('disconnectWebSocket doit avoir le type [Progress] Disconnect WebSocket', () => {
    const action = ProgressActions.disconnectWebSocket();
    expect(action.type).toBe('[Progress] Disconnect WebSocket');
  });
});
