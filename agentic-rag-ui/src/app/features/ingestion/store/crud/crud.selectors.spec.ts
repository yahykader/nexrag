import { describe, it, expect } from 'vitest';
import {
  selectCrudLoading,
  selectCrudError,
  selectDeleteOperations,
  selectPendingDeleteOperations,
  selectSuccessDeleteOperations,
  selectSystemHealthy,
  selectTotalEmbeddings,
  selectDuplicateCheckByFilename,
  selectBatchInfoById
} from './crud.selectors';
import { initialCrudState } from './crud.state';

describe('CrudSelectors', () => {
  const baseState = { crud: initialCrudState };

  it('selectCrudLoading doit retourner true quand loading est true', () => {
    const state = { crud: { ...initialCrudState, loading: true } };
    expect(selectCrudLoading(state as any)).toBe(true);
  });

  it('selectCrudError doit retourner le message d\'erreur', () => {
    const state = { crud: { ...initialCrudState, error: 'Erreur réseau' } };
    expect(selectCrudError(state as any)).toBe('Erreur réseau');
  });

  it('selectDeleteOperations doit retourner le tableau d\'opérations', () => {
    const ops = [{ id: 'op-1', type: 'file' as const, targetId: 'emb-1', status: 'pending' as const, timestamp: new Date() }];
    const state = { crud: { ...initialCrudState, deleteOperations: ops } };
    expect(selectDeleteOperations(state as any)).toEqual(ops);
  });

  it('selectPendingDeleteOperations doit filtrer par status pending', () => {
    const ops = [
      { id: 'op-1', type: 'file' as const, targetId: 'emb-1', status: 'pending' as const, timestamp: new Date() },
      { id: 'op-2', type: 'file' as const, targetId: 'emb-2', status: 'success' as const, timestamp: new Date() }
    ];
    const state = { crud: { ...initialCrudState, deleteOperations: ops } };
    const pending = selectPendingDeleteOperations(state as any);
    expect(pending).toHaveLength(1);
    expect(pending[0].id).toBe('op-1');
  });

  it('selectSuccessDeleteOperations doit filtrer par status success', () => {
    const ops = [
      { id: 'op-1', type: 'file' as const, targetId: 'emb-1', status: 'pending' as const, timestamp: new Date() },
      { id: 'op-2', type: 'file' as const, targetId: 'emb-2', status: 'success' as const, timestamp: new Date() }
    ];
    const state = { crud: { ...initialCrudState, deleteOperations: ops } };
    const success = selectSuccessDeleteOperations(state as any);
    expect(success).toHaveLength(1);
    expect(success[0].id).toBe('op-2');
  });

  it('selectSystemHealthy doit retourner false quand redisHealthy est undefined', () => {
    expect(selectSystemHealthy(baseState as any)).toBe(false);
  });

  it('selectTotalEmbeddings doit retourner 0 quand totalEmbeddings est undefined', () => {
    expect(selectTotalEmbeddings(baseState as any)).toBe(0);
  });
});
