import { describe, it, expect } from 'vitest';
import {
  selectWebSocketConnected,
  selectActiveProgress,
  selectRecentlyCompleted,
  selectActiveProgressCount,
} from './progress.selectors';
import { initialProgressState } from './progress.state';
import { mockUploadProgress } from '../../../../test-helpers';

describe('ProgressSelectors', () => {

  // ── selectWebSocketConnected (dépend de selectProgressState) → projector reçoit feature state ──

  it('selectWebSocketConnected doit retourner false par défaut', () => {
    expect(selectWebSocketConnected.projector(initialProgressState)).toBe(false);
  });

  // ── selectActiveProgress (dépend de selectAllProgress) → projector reçoit UploadProgress[] ──

  it('selectActiveProgress doit filtrer les stages COMPLETED et ERROR et _shouldClear', () => {
    const allProgress = [
      mockUploadProgress({ batchId: 'b1', stage: 'PROCESSING' }),
      mockUploadProgress({ batchId: 'b2', stage: 'COMPLETED' }),
      mockUploadProgress({ batchId: 'b3', stage: 'EMBEDDING', _shouldClear: true }),
    ];

    const active = selectActiveProgress.projector(allProgress);
    expect(active).toHaveLength(1);
    expect(active[0].batchId).toBe('b1');
  });

  // ── selectRecentlyCompleted (dépend de selectAllProgress) → projector reçoit UploadProgress[] ──

  it('selectRecentlyCompleted doit retourner les COMPLETED/ERROR avec _clearAt > Date.now()', () => {
    const futureTs = Date.now() + 10000;
    const pastTs = Date.now() - 1000;

    const allProgress = [
      mockUploadProgress({ batchId: 'b1', stage: 'COMPLETED', _clearAt: futureTs, _shouldClear: true }),
      mockUploadProgress({ batchId: 'b2', stage: 'COMPLETED', _clearAt: pastTs,   _shouldClear: true }),
      mockUploadProgress({ batchId: 'b3', stage: 'PROCESSING' }),
    ];

    const recent = selectRecentlyCompleted.projector(allProgress);
    expect(recent).toHaveLength(1);
    expect(recent[0].batchId).toBe('b1');
  });

  // ── selectActiveProgressCount (dépend de selectActiveProgress) → projector reçoit UploadProgress[] actifs ──

  it('selectActiveProgressCount doit retourner le nombre de progress actifs', () => {
    const activeProgress = [
      mockUploadProgress({ batchId: 'b1', stage: 'PROCESSING' }),
      mockUploadProgress({ batchId: 'b2', stage: 'EMBEDDING' }),
    ];

    expect(selectActiveProgressCount.projector(activeProgress)).toBe(2);
  });
});