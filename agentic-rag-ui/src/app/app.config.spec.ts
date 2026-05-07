import { describe, expect, it } from 'vitest';

import { appConfig } from './app.config';

describe('AppConfig', () => {
  const seen = new WeakSet();
  const serialized = JSON.stringify(appConfig.providers, (_key, value) => {
    if (typeof value === 'function') return value.name;
    if (typeof value === 'object' && value !== null) {
      if (seen.has(value)) return '[Circular]';
      seen.add(value);
    }
    return value;
  });

  it('doit inclure provideRouter() avec les routes de l\'application', () => {
    expect(serialized).toContain('workspace');
  });

  it('doit inclure provideStore() avec les 5 slices (ingestion, progress, crud, rateLimit, chat)', () => {
    expect(serialized).toContain('ingestion');
    expect(serialized).toContain('progress');
    expect(serialized).toContain('crud');
    expect(serialized).toContain('rateLimit');
    expect(serialized).toContain('chat');
  });

  it('doit inclure provideEffects() avec les 5 classes d\'effets', () => {
    expect(serialized).toContain('IngestionEffects');
    expect(serialized).toContain('ProgressEffects');
    expect(serialized).toContain('CrudEffects');
    expect(serialized).toContain('RateLimitEffects');
    expect(serialized).toContain('ChatEffects');
  });

  it('doit inclure provideHttpClient() avec les intercepteurs duplicate et rate-limit', () => {
    expect(serialized).toContain('duplicateInterceptor');
    expect(serialized).toContain('rateLimitInterceptor');
  });
});
