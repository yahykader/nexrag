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

  it('selectIsRateLimited doit retourner false par défaut', () => {
    expect(selectIsRateLimited.projector(initialRateLimitState)).toBe(false);
  });

  it('selectRetryAfterSeconds doit retourner 0 par défaut', () => {
    expect(selectRetryAfterSeconds.projector(initialRateLimitState)).toBe(0);
  });

  it('selectRateLimitMessage doit retourner chaîne vide par défaut', () => {
    expect(selectRateLimitMessage.projector(initialRateLimitState)).toBe('');
  });

  it('selectUploadRemaining doit retourner null par défaut', () => {
    expect(selectUploadRemaining.projector(initialRateLimitState)).toBeNull();
  });

  it('selectRateLimitPercentage doit retourner 100 quand upload est null', () => {
    expect(selectRateLimitPercentage.projector(initialRateLimitState)).toBe(100);
  });

  it('selectRateLimitPercentage doit calculer le pourcentage (upload=5, limit=10 → 50)', () => {
    const state = {
      ...initialRateLimitState,
      remainingTokens: { ...initialRateLimitState.remainingTokens, upload: 5 }
    };
    expect(selectRateLimitPercentage.projector(state)).toBe(50);
  });
});