import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { Action } from '@ngrx/store';
import { provideMockActions } from '@ngrx/effects/testing';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { ReplaySubject } from 'rxjs';

import { RateLimitEffects } from './rate-limit.effects';
import * as RateLimitActions from './rate-limit.actions';
import * as IngestionActions from '../ingestion/ingestion.actions';
import { initialRateLimitState } from './rate-limit.state';

describe('RateLimitEffects', () => {
  let spectator: SpectatorService<RateLimitEffects>;
  let actions$: ReplaySubject<Action>;
  let store: MockStore;

  const createService = createServiceFactory({
    service: RateLimitEffects,
    providers: [
      provideMockActions(() => actions$),
      provideMockStore({ initialState: { rateLimit: initialRateLimitState } }),
    ],
  });

  beforeEach(() => {
    actions$ = new ReplaySubject<Action>(1);
    spectator = createService();
    store = spectator.inject(MockStore);
  });

  afterEach(() => {
    store.resetSelectors();
    vi.useRealTimers();
  });

  describe('startCountdown$', () => {
    it('doit émettre decrementCountdown après 1 seconde', () => {
      vi.useFakeTimers();

      const results: Action[] = [];
      spectator.service.startCountdown$.subscribe(a => results.push(a));
      actions$.next(RateLimitActions.rateLimitExceeded({ retryAfterSeconds: 3, message: 'test' }));

      vi.advanceTimersByTime(1000);

      expect(results).toHaveLength(1);
      expect(results[0].type).toBe('[Rate Limit] Decrement Countdown');
    });
  });

  it('handleUploadRateLimited$ (FR-016) doit mapper uploadFileRateLimited vers rateLimitExceeded', () => {
    const results: Action[] = [];
    spectator.service.handleUploadRateLimited$.subscribe(a => results.push(a));

    actions$.next(IngestionActions.uploadFileRateLimited({
      fileId: 'f1',
      retryAfterSeconds: 45,
      message: 'Limite atteinte'
    }));

    expect(results).toHaveLength(1);
    expect(results[0].type).toBe('[Rate Limit] Exceeded');
    expect((results[0] as any).retryAfterSeconds).toBe(45);
    expect((results[0] as any).message).toBe('Limite atteinte');
  });

  it('autoReset$ doit dispatcher rateLimitReset quand retryAfterSeconds est 0 dans le store', () => {
    store.setState({ rateLimit: { ...initialRateLimitState, isRateLimited: true, retryAfterSeconds: 0 } });
    store.refreshState();

    const dispatched: Action[] = [];
    (vi.spyOn(store, 'dispatch') as any).mockImplementation((action: Action) => dispatched.push(action));

    spectator.service.autoReset$.subscribe();
    actions$.next(RateLimitActions.decrementCountdown());

    expect(dispatched.some(a => a.type === '[Rate Limit] Reset')).toBe(true);
  });
});
