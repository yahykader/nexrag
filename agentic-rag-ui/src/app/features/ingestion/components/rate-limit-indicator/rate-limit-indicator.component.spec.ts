import { CommonModule } from '@angular/common';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { describe, it, expect, beforeEach } from 'vitest';

import { RateLimitIndicatorComponent } from './rate-limit-indicator.component';
import { selectUploadRemaining } from '../../store/rate-limit/rate-limit.selectors';
import { mockRateLimitState } from '../testing/ingestion-test.helpers';

describe('RateLimitIndicatorComponent', () => {
  const createComponent = createComponentFactory({
    component: RateLimitIndicatorComponent,
    imports: [CommonModule],
    providers: [provideMockStore({ initialState: mockRateLimitState() })],
  });

  let spectator: Spectator<RateLimitIndicatorComponent>;
  let mockStore: MockStore;

  beforeEach(() => {
    spectator = createComponent();
    mockStore = spectator.inject(MockStore);
  });

  it('doit se masquer quand isRateLimited est false', () => {
    // initial state has remainingTokens.upload: null → *ngIf falsy → hidden
    mockStore.overrideSelector(selectUploadRemaining, null);
    mockStore.refreshState();
    spectator.detectChanges();
    expect(spectator.query('.rate-limit-indicator')).toBeNull();
  });

  it('doit afficher le compte à rebours quand isRateLimited est true', () => {
    mockStore.overrideSelector(selectUploadRemaining, 30);
    mockStore.refreshState();
    spectator.detectChanges();
    expect(spectator.query('.rate-limit-indicator')).toBeTruthy();
    expect(spectator.element.textContent).toContain('30');
  });

  it('doit mettre à jour l\'affichage quand le store émet une nouvelle valeur de jetons restants', () => {
    mockStore.overrideSelector(selectUploadRemaining, 8);
    mockStore.refreshState();
    spectator.detectChanges();
    expect(spectator.element.textContent).toContain('8');
  });
});
