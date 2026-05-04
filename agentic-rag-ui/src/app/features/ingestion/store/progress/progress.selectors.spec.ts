import { describe, it, expect } from 'vitest';
import {
  selectWebSocketConnected,
  selectWebSocketConnecting,
  selectActiveProgress,
  selectRecentlyCompleted,
  selectActiveProgressCount,
  selectWebSocketError
} from './progress.selectors';
import { initialProgressState } from './progress.state';
import { mockUploadProgress } from '../../../../test-helpers';

describe('ProgressSelectors', () => {
  const baseState = { progress: initialProgressState };

  it('selectWebSocketConnected doit retourner false par défaut', () => {
    expect(selectWebSocketConnected(baseState as any)).toBe(false);
  });

  it('selectActiveProgress doit filtrer les stages COMPLETED et ERROR et _shouldClear', () => {
    const state = {
      progress: {
        ...initialProgressState,
        progressByBatch: {
          'b1': mockUploadProgress({ batchId: 'b1', stage: 'PROCESSING' }),
          'b2': mockUploadProgress({ batchId: 'b2', stage: 'COMPLETED' }),
          'b3': mockUploadProgress({ batchId: 'b3', stage: 'EMBEDDING', _shouldClear: true }),
        }
      }
    };
    const active = selectActiveProgress(state as any);
    expect(active).toHaveLength(1);
    expect(active[0].batchId).toBe('b1');
  });

  it('selectRecentlyCompleted doit retourner les COMPLETED/ERROR avec _clearAt > Date.now()', () => {
    const futureTs = Date.now() + 10000;
    const pastTs = Date.now() - 1000;
    const state = {
      progress: {
        ...initialProgressState,
        progressByBatch: {
          'b1': mockUploadProgress({ batchId: 'b1', stage: 'COMPLETED', _clearAt: futureTs, _shouldClear: true }),
          'b2': mockUploadProgress({ batchId: 'b2', stage: 'COMPLETED', _clearAt: pastTs, _shouldClear: true }),
          'b3': mockUploadProgress({ batchId: 'b3', stage: 'PROCESSING' }),
        }
      }
    };
    const recent = selectRecentlyCompleted(state as any);
    expect(recent).toHaveLength(1);
    expect(recent[0].batchId).toBe('b1');
  });

  it('selectActiveProgressCount doit retourner le nombre de progress actifs', () => {
    const state = {
      progress: {
        ...initialProgressState,
        progressByBatch: {
          'b1': mockUploadProgress({ batchId: 'b1', stage: 'PROCESSING' }),
          'b2': mockUploadProgress({ batchId: 'b2', stage: 'EMBEDDING' }),
          'b3': mockUploadProgress({ batchId: 'b3', stage: 'COMPLETED' }),
        }
      }
    };
    expect(selectActiveProgressCount(state as any)).toBe(2);
  });
});
