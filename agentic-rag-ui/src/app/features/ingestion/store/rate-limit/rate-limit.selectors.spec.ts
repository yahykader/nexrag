import { describe, it, expect, beforeEach } from 'vitest';
import {
  selectIsRateLimited,
  selectRetryAfterSeconds,
  selectRateLimitMessage,
  selectRemainingTokens,
  selectUploadRemaining,
  selectRateLimitPercentage,
  selectRateLimitState
} from './rate-limit.selectors';
import { initialRateLimitState } from './rate-limit.state';
import { releaseSelectors } from '../../../../test-helpers';

describe('RateLimitSelectors', () => {
  let baseState: any;

  beforeEach(() => {
    // reset memoization NgRx
    releaseSelectors([
      selectRateLimitState,
      selectIsRateLimited,
      selectRetryAfterSeconds,
      selectRateLimitMessage,
      selectRemainingTokens,
      selectUploadRemaining,
      selectRateLimitPercentage
    ]);

    // ⚠️ clone profond pour éviter toute mutation partagée
    baseState = {
      rateLimit: structuredClone(initialRateLimitState)
    };
  });

  it('selectIsRateLimited doit retourner false par défaut', () => {
    expect(selectIsRateLimited(baseState)).toBe(false);
  });

  it('selectRetryAfterSeconds doit retourner 0 par défaut', () => {
    expect(selectRetryAfterSeconds(baseState)).toBe(0);
  });

  it('selectRateLimitMessage doit retourner chaîne vide par défaut', () => {
    expect(selectRateLimitMessage(baseState)).toBe('');
  });

  it('selectUploadRemaining doit retourner null par défaut', () => {
    expect(selectUploadRemaining(baseState)).toBeNull();
  });

  it('selectRateLimitPercentage doit retourner 100 quand upload est null', () => {
    expect(selectRateLimitPercentage(baseState)).toBe(100);
  });

  it('selectRateLimitPercentage doit calculer le pourcentage (upload=5, limit=10 → 50)', () => {
    const state = {
      rateLimit: {
        ...structuredClone(initialRateLimitState),
        remainingTokens: {
          ...structuredClone(initialRateLimitState).remainingTokens,
          upload: 5
        }
      }
    };

    expect(selectRateLimitPercentage(state)).toBe(50);
  });
});