import { describe, it, expect } from 'vitest';
import { rateLimitReducer } from './rate-limit.reducer';
import { initialRateLimitState } from './rate-limit.state';
import * as RateLimitActions from './rate-limit.actions';

describe('RateLimitReducer', () => {
  it('doit retourner l\'état initial', () => {
    const state = rateLimitReducer(undefined, { type: '@@INIT' });
    expect(state).toEqual(initialRateLimitState);
  });

  it('rateLimitExceeded doit passer isRateLimited à true et stocker retryAfterSeconds', () => {
    const action = RateLimitActions.rateLimitExceeded({ message: 'Rate limit', retryAfterSeconds: 60 });
    const state = rateLimitReducer(initialRateLimitState, action);
    expect(state.isRateLimited).toBe(true);
    expect(state.retryAfterSeconds).toBe(60);
    expect(state.message).toBe('Rate limit');
  });

  it('rateLimitReset doit remettre isRateLimited à false et retryAfterSeconds à 0', () => {
    const exceeded = rateLimitReducer(initialRateLimitState, RateLimitActions.rateLimitExceeded({ message: 'msg', retryAfterSeconds: 30 }));
    const state = rateLimitReducer(exceeded, RateLimitActions.rateLimitReset());
    expect(state.isRateLimited).toBe(false);
    expect(state.retryAfterSeconds).toBe(0);
    expect(state.message).toBe('');
  });

  it('updateRemainingTokens doit mettre à jour uniquement le endpoint spécifié', () => {
    const state = rateLimitReducer(initialRateLimitState, RateLimitActions.updateRemainingTokens({ endpoint: 'upload', remaining: 7 }));
    expect(state.remainingTokens.upload).toBe(7);
    expect(state.remainingTokens.search).toBeNull();
    expect(state.remainingTokens.batch).toBeNull();
  });

  it('decrementCountdown doit décrémenter retryAfterSeconds de 1', () => {
    const withCountdown = { ...initialRateLimitState, retryAfterSeconds: 5 };
    const state = rateLimitReducer(withCountdown, RateLimitActions.decrementCountdown());
    expect(state.retryAfterSeconds).toBe(4);
  });

  it('decrementCountdown doit utiliser Math.max(0, ...) pour prévenir les valeurs négatives', () => {
    const atZero = { ...initialRateLimitState, retryAfterSeconds: 0 };
    const state = rateLimitReducer(atZero, RateLimitActions.decrementCountdown());
    expect(state.retryAfterSeconds).toBe(0);
  });

  it('updateRemainingTokens pour endpoint delete doit mettre à jour correctly', () => {
    const state = rateLimitReducer(initialRateLimitState, RateLimitActions.updateRemainingTokens({ endpoint: 'delete', remaining: 15 }));
    expect(state.remainingTokens.delete).toBe(15);
  });

  it('rateLimitExceeded suivi de decrementCountdown doit décrémenter le bon compteur', () => {
    const exceeded = rateLimitReducer(initialRateLimitState, RateLimitActions.rateLimitExceeded({ message: 'msg', retryAfterSeconds: 3 }));
    const decremented = rateLimitReducer(exceeded, RateLimitActions.decrementCountdown());
    expect(decremented.retryAfterSeconds).toBe(2);
    expect(decremented.isRateLimited).toBe(true);
  });
});
