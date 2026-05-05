import { CommonModule } from '@angular/common';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { UploadZoneComponent } from './upload-zone.component';
import { selectIsRateLimited } from '../../store/rate-limit/rate-limit.selectors';
import { mockRateLimitState } from '../testing/ingestion-test.helpers';

describe('UploadZoneComponent', () => {
  const createComponent = createComponentFactory({
    component: UploadZoneComponent,
    imports: [CommonModule],
    providers: [provideMockStore({ initialState: mockRateLimitState() })],
  });

  let spectator: Spectator<UploadZoneComponent>;
  let mockStore: MockStore;

  beforeEach(() => {
    spectator = createComponent();
    mockStore = spectator.inject(MockStore);
  });

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  it('doit appliquer la classe "dragging" lors du dragover', () => {
    const event = { preventDefault: () => {}, stopPropagation: () => {} } as unknown as DragEvent;
    spectator.component.onDragOver(event);
    spectator.detectChanges();
    expect(spectator.component.isDragging).toBe(true);
    expect(spectator.query('.upload-zone')).toHaveClass('dragging');
  });

  it('doit émettre filesSelected avec les fichiers valides au drop', () => {
    const file = new File(['hello'], 'test.pdf', { type: 'application/pdf' });
    const filesSpy = vi.fn();
    spectator.output('filesSelected').subscribe(filesSpy);
    const event = {
      preventDefault: () => {},
      stopPropagation: () => {},
      dataTransfer: { files: [file] as unknown as FileList },
    } as unknown as DragEvent;
    spectator.component.onDrop(event);
    expect(filesSpy).toHaveBeenCalledWith([file]);
  });

  it('doit ne pas émettre filesSelected pour un fichier de taille 0', () => {
    const emptyFile = new File([], 'empty.pdf', { type: 'application/pdf' });
    const filesSpy = vi.fn();
    spectator.output('filesSelected').subscribe(filesSpy);
    const event = {
      preventDefault: () => {},
      stopPropagation: () => {},
      dataTransfer: { files: [emptyFile] as unknown as FileList },
    } as unknown as DragEvent;
    spectator.component.onDrop(event);
    expect(filesSpy).not.toHaveBeenCalled();
  });

  it('doit afficher la taille max autorisée formatée', () => {
    spectator.setInput('maxFileSize', 5 * 1024 * 1024 * 1024);
    spectator.detectChanges();
    const text = spectator.element.textContent ?? '';
    expect(text).toContain('5');
    expect(text).toContain('GB');
  });

  it('doit désactiver la zone quand isRateLimited est true', () => {
    mockStore.overrideSelector(selectIsRateLimited, true);
    mockStore.refreshState();
    spectator.detectChanges();
    expect(spectator.query('.upload-zone')).toHaveClass('disabled');
  });

  it('doit réinitialiser isDragging au dragleave', () => {
    spectator.component.isDragging = true;
    const event = { preventDefault: () => {}, stopPropagation: () => {} } as unknown as DragEvent;
    spectator.component.onDragLeave(event);
    expect(spectator.component.isDragging).toBe(false);
  });

  it('doit ne pas émettre filesSelected pour un fichier trop grand', () => {
    const bigFile = new File(['x'.repeat(10)], 'big.pdf', { type: 'application/pdf' });
    Object.defineProperty(bigFile, 'size', { value: 6 * 1024 * 1024 * 1024 });
    const filesSpy = vi.fn();
    spectator.output('filesSelected').subscribe(filesSpy);
    const event = {
      preventDefault: () => {},
      stopPropagation: () => {},
      dataTransfer: { files: [bigFile] as unknown as FileList },
    } as unknown as DragEvent;
    spectator.component.onDrop(event);
    expect(filesSpy).not.toHaveBeenCalled();
  });

  it('doit émettre filesSelected via onFileSelected', () => {
    const file = new File(['content'], 'selected.pdf', { type: 'application/pdf' });
    const filesSpy = vi.fn();
    spectator.output('filesSelected').subscribe(filesSpy);
    const mockInput = { files: [file] as unknown as FileList, value: '' } as HTMLInputElement;
    const event = { target: mockInput } as unknown as Event;
    spectator.component.onFileSelected(event);
    expect(filesSpy).toHaveBeenCalledWith([file]);
  });
});
