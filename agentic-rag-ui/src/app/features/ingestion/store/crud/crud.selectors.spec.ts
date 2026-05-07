import { describe, it, expect } from 'vitest';
import {
  selectCrudLoading,
  selectCrudError,
  selectDeleteOperations,
  selectPendingDeleteOperations,
  selectSuccessDeleteOperations,
  selectSystemHealthy,
  selectTotalEmbeddings,
} from './crud.selectors';
import { initialCrudState } from './crud.state';

describe('CrudSelectors', () => {

  // ── Directs (dépendent de selectCrudState) → projector reçoit le feature state ──

  it('selectCrudLoading doit retourner true quand loading est true', () => {
    expect(selectCrudLoading.projector({ ...initialCrudState, loading: true })).toBe(true);
  });

  it("selectCrudError doit retourner le message d'erreur", () => {
    expect(selectCrudError.projector({ ...initialCrudState, error: 'Erreur réseau' })).toBe('Erreur réseau');
  });

  it("selectDeleteOperations doit retourner le tableau d'opérations", () => {
    const ops = [{ id: 'op-1', type: 'file' as const, targetId: 'emb-1', status: 'pending' as const, timestamp: new Date() }];
    expect(selectDeleteOperations.projector({ ...initialCrudState, deleteOperations: ops })).toEqual(ops);
  });

  // ── Composés (dépendent de selectDeleteOperations) → projector reçoit ops[] ──

  it('selectPendingDeleteOperations doit filtrer par status pending', () => {
    const ops = [
      { id: 'op-1', type: 'file' as const, targetId: 'emb-1', status: 'pending' as const, timestamp: new Date() },
      { id: 'op-2', type: 'file' as const, targetId: 'emb-2', status: 'success' as const, timestamp: new Date() },
    ];
    const pending = selectPendingDeleteOperations.projector(ops);
    expect(pending).toHaveLength(1);
    expect(pending[0].id).toBe('op-1');
  });

  it('selectSuccessDeleteOperations doit filtrer par status success', () => {
    const ops = [
      { id: 'op-1', type: 'file' as const, targetId: 'emb-1', status: 'pending' as const, timestamp: new Date() },
      { id: 'op-2', type: 'file' as const, targetId: 'emb-2', status: 'success' as const, timestamp: new Date() },
    ];
    const success = selectSuccessDeleteOperations.projector(ops);
    expect(success).toHaveLength(1);
    expect(success[0].id).toBe('op-2');
  });

  // ── Composés (dépendent de selectSystemStats) → projector reçoit stats ──

  it('selectSystemHealthy doit retourner false quand redisHealthy est undefined', () => {
    expect(selectSystemHealthy.projector(initialCrudState.systemStats)).toBe(false);
  });

  it('selectTotalEmbeddings doit retourner 0 quand totalEmbeddings est undefined', () => {
    expect(selectTotalEmbeddings.projector(initialCrudState.systemStats)).toBe(0);
  });
});