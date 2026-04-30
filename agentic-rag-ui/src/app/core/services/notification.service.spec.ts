// notification.service.spec.ts — Phase 3, User Story 5
// Vérifie les émissions de toasts et l'unicité des IDs

import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { NotificationService, Toast } from './notification.service';

describe('NotificationService', () => {
  let spectator: SpectatorService<NotificationService>;

  const createService = createServiceFactory({
    service: NotificationService,
  });

  beforeEach(() => {
    spectator = createService();
  });

  // ─── US5 — AC1 ────────────────────────────────────────────────────────────

  it('doit émettre un toast de type "success" avec le bon titre et message', () => {
    let captured: Toast | undefined;
    spectator.service.toasts$.subscribe((t) => (captured = t));

    spectator.service.success('Succès', 'Fichier uploadé');

    expect(captured).toBeDefined();
    expect(captured!.type).toBe('success');
    expect(captured!.title).toBe('Succès');
    expect(captured!.message).toBe('Fichier uploadé');
    expect(captured!.duration).toBe(5000);
  });

  // ─── US5 — AC2 ────────────────────────────────────────────────────────────

  it('doit émettre un toast de type "error" avec la durée personnalisée', () => {
    let captured: Toast | undefined;
    spectator.service.toasts$.subscribe((t) => (captured = t));

    spectator.service.error('Erreur', 'Connexion refusée', 8000);

    expect(captured).toBeDefined();
    expect(captured!.type).toBe('error');
    expect(captured!.title).toBe('Erreur');
    expect(captured!.message).toBe('Connexion refusée');
    expect(captured!.duration).toBe(8000);
  });

  // ─── US5 — AC3 ────────────────────────────────────────────────────────────

  it('doit émettre un toast de type "warning"', () => {
    let captured: Toast | undefined;
    spectator.service.toasts$.subscribe((t) => (captured = t));

    spectator.service.warning('Attention', 'Limite approchée');

    expect(captured!.type).toBe('warning');
  });

  // ─── US5 — AC4 ────────────────────────────────────────────────────────────

  it('doit émettre un toast de type "info"', () => {
    let captured: Toast | undefined;
    spectator.service.toasts$.subscribe((t) => (captured = t));

    spectator.service.info('Info', 'Traitement en cours');

    expect(captured!.type).toBe('info');
  });

  // ─── US5 — AC5 ────────────────────────────────────────────────────────────

  it('doit générer des IDs uniques pour deux toasts créés dans le même milliseconde', () => {
    const ids: string[] = [];
    spectator.service.toasts$.subscribe((t) => ids.push(t.id));

    vi.useFakeTimers();
    vi.setSystemTime(1714478000000);

    spectator.service.success('A', 'premier');
    spectator.service.success('B', 'second');

    vi.useRealTimers();

    expect(ids).toHaveLength(2);
    expect(ids[0]).toBe('1714478000000-' + (parseInt(ids[0].split('-')[1])));
    expect(ids[1]).toBe('1714478000000-' + (parseInt(ids[1].split('-')[1])));
    expect(ids[0]).not.toBe(ids[1]);
    // Les suffixes sont des entiers consécutifs
    const n0 = parseInt(ids[0].split('-')[1]);
    const n1 = parseInt(ids[1].split('-')[1]);
    expect(n1).toBe(n0 + 1);
  });
});
