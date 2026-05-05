import { CommonModule } from '@angular/common';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { describe, it, expect, beforeEach } from 'vitest';

import { RateLimitToastComponent } from './rate-limit-toast.component';
import { selectIsRateLimited, selectRateLimitMessage } from '../../store/rate-limit/rate-limit.selectors';
import { mockRateLimitState } from '../testing/ingestion-test.helpers';

describe('RateLimitToastComponent', () => {
  const createComponent = createComponentFactory({
    component: RateLimitToastComponent,
    imports: [CommonModule, NoopAnimationsModule],
    providers: [
      provideMockStore({ initialState: mockRateLimitState() }),
    ],
  });

  let spectator: Spectator<RateLimitToastComponent>;
  let mockStore: MockStore;

  beforeEach(() => {
    spectator = createComponent();
    mockStore = spectator.inject(MockStore);
  });

  it('doit afficher le message de rate limit quand isRateLimited est true', () => {
    mockStore.overrideSelector(selectIsRateLimited, true);
    mockStore.overrideSelector(selectRateLimitMessage, 'Limite dépassée');
    mockStore.refreshState();
    spectator.detectChanges();
    expect(spectator.query('.rate-limit-toast')).toBeTruthy();
    expect(spectator.element.textContent).toContain('Limite dépassée');
  });

  it('doit se masquer quand isRateLimited repasse à false', () => {
    mockStore.overrideSelector(selectIsRateLimited, true);
    mockStore.refreshState();
    spectator.detectChanges();
    expect(spectator.query('.rate-limit-toast')).toBeTruthy();

    mockStore.overrideSelector(selectIsRateLimited, false);
    mockStore.refreshState();
    spectator.detectChanges();
    expect(spectator.query('.rate-limit-toast')).toBeNull();
  });
});
