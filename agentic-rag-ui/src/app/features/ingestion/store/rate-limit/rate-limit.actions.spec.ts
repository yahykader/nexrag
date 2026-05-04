import { describe, it, expect } from 'vitest';
import * as RateLimitActions from './rate-limit.actions';

describe('RateLimitActions', () => {
  it('rateLimitExceeded doit avoir le type [Rate Limit] Exceeded', () => {
    const action = RateLimitActions.rateLimitExceeded({ message: 'Rate limit dépassé', retryAfterSeconds: 60 });
    expect(action.type).toBe('[Rate Limit] Exceeded');
    expect(action.retryAfterSeconds).toBe(60);
    expect(action.message).toBe('Rate limit dépassé');
  });

  it('rateLimitReset doit avoir le type [Rate Limit] Reset', () => {
    const action = RateLimitActions.rateLimitReset();
    expect(action.type).toBe('[Rate Limit] Reset');
  });

  it('updateRemainingTokens doit avoir le type [Rate Limit] Update Remaining Tokens', () => {
    const action = RateLimitActions.updateRemainingTokens({ endpoint: 'upload', remaining: 5 });
    expect(action.type).toBe('[Rate Limit] Update Remaining Tokens');
    expect(action.endpoint).toBe('upload');
    expect(action.remaining).toBe(5);
  });

  it('startCountdown doit avoir le type [Rate Limit] Start Countdown', () => {
    const action = RateLimitActions.startCountdown({ seconds: 30 });
    expect(action.type).toBe('[Rate Limit] Start Countdown');
    expect(action.seconds).toBe(30);
  });

  it('decrementCountdown doit avoir le type [Rate Limit] Decrement Countdown', () => {
    const action = RateLimitActions.decrementCountdown();
    expect(action.type).toBe('[Rate Limit] Decrement Countdown');
  });

  it('updateRemainingTokens pour endpoint search doit stocker remaining', () => {
    const action = RateLimitActions.updateRemainingTokens({ endpoint: 'search', remaining: 45 });
    expect(action.endpoint).toBe('search');
    expect(action.remaining).toBe(45);
  });
});
