import { describe, it, expect } from 'vitest';
import {
  selectIsRateLimited,
  selectRetryAfterSeconds,
  selectRateLimitMessage,
  selectRemainingTokens,
  selectUploadRemaining,
  selectRateLimitPercentage
} from './rate-limit.selectors';
import { initialRateLimitState } from './rate-limit.state';

describe('RateLimitSelectors', () => {
  const baseState = { rateLimit: initialRateLimitState };

  it('selectIsRateLimited doit retourner false par défaut', () => {
    expect(selectIsRateLimited(baseState as any)).toBe(false);
  });

  it('selectRetryAfterSeconds doit retourner 0 par défaut', () => {
    expect(selectRetryAfterSeconds(baseState as any)).toBe(0);
  });

  it('selectRateLimitMessage doit retourner chaîne vide par défaut', () => {
    expect(selectRateLimitMessage(baseState as any)).toBe('');
  });

  it('selectUploadRemaining doit retourner null par défaut', () => {
    expect(selectUploadRemaining(baseState as any)).toBeNull();
  });

  it('selectRateLimitPercentage doit retourner 100 quand upload est null', () => {
    expect(selectRateLimitPercentage(baseState as any)).toBe(100);
  });

  it('selectRateLimitPercentage doit calculer le pourcentage (upload=5, limit=10 → 50)', () => {
    const state = {
      rateLimit: {
        ...initialRateLimitState,
        remainingTokens: { ...initialRateLimitState.remainingTokens, upload: 5 }
      }
    };
    expect(selectRateLimitPercentage(state as any)).toBe(50);
  });
});
