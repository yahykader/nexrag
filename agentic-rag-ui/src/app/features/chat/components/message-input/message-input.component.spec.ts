import { NO_ERRORS_SCHEMA } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { MessageInputComponent } from './message-input.component';

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('MessageInputComponent', () => {
  let spectator: Spectator<MessageInputComponent>;

  const createComponent = createComponentFactory({
    component: MessageInputComponent,
    schemas: [NO_ERRORS_SCHEMA],
  });

  beforeEach(() => {
    spectator = createComponent({ props: { inputText: '' } });
  });

  afterEach(() => vi.restoreAllMocks());

  // ── Smoke ─────────────────────────────────────────────────────────────────

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  // ── Empty guard ───────────────────────────────────────────────────────────

  it('doit désactiver le bouton Submit si le champ est vide', () => {
    spectator.setInput('inputText', '');
    const btn = spectator.query('button.send-button') as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  // ── Click submit ──────────────────────────────────────────────────────────

  it('doit émettre send au click submit avec le contenu saisi', () => {
    const sendSpy = vi.fn();
    spectator.output('send').subscribe(sendSpy);
    spectator.setInput('inputText', 'Bonjour');
    spectator.click('button.send-button');
    expect(sendSpy).toHaveBeenCalledWith('Bonjour');
  });

  // ── Ctrl+Enter submit ─────────────────────────────────────────────────────

  it('doit émettre send au Ctrl+Enter quand le champ est rempli', () => {
    const sendSpy = vi.fn();
    spectator.output('send').subscribe(sendSpy);
    spectator.setInput('inputText', 'Bonjour');
    const textarea = spectator.query('textarea')!;
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true, bubbles: true }));
    expect(sendSpy).toHaveBeenCalledWith('Bonjour');
  });

  // ── Ctrl+Enter empty guard ────────────────────────────────────────────────

  it('doit ne pas émettre send au Ctrl+Enter si le champ est vide', () => {
    const sendSpy = vi.fn();
    spectator.output('send').subscribe(sendSpy);
    spectator.setInput('inputText', '');
    const textarea = spectator.query('textarea')!;
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true, bubbles: true }));
    expect(sendSpy).not.toHaveBeenCalled();
  });

  // ── Whitespace guard ──────────────────────────────────────────────────────

  it('doit ne pas émettre send si le champ contient uniquement des espaces', () => {
    const sendSpy = vi.fn();
    spectator.output('send').subscribe(sendSpy);
    spectator.setInput('inputText', '   ');
    spectator.click('button.send-button');
    expect(sendSpy).not.toHaveBeenCalled();
  });

  // ── Disabled guard ────────────────────────────────────────────────────────

  it('doit ne pas émettre send quand disabled=true', () => {
    const sendSpy = vi.fn();
    spectator.output('send').subscribe(sendSpy);
    spectator.setInput('disabled', true);
    spectator.setInput('inputText', 'Hello');
    spectator.component.onSend();
    expect(sendSpy).not.toHaveBeenCalled();
  });

  // ── Streaming cancel ──────────────────────────────────────────────────────

  it('doit afficher le bouton Annuler quand isStreaming=true', () => {
    const cancelSpy = vi.fn();
    spectator.output('cancel').subscribe(cancelSpy);
    spectator.setInput('isStreaming', true);
    expect(spectator.query('.streaming-indicator')).toBeTruthy();
    spectator.click('.streaming-indicator .btn-outline-danger');
    expect(cancelSpy).toHaveBeenCalled();
  });

  // ── Voice handlers ────────────────────────────────────────────────────────

  it('doit émettre inputChange quand onInputChange est appelé', () => {
    const inputChangeSpy = vi.fn();
    spectator.output('inputChange').subscribe(inputChangeSpy);
    spectator.component.onInputChange('test');
    expect(inputChangeSpy).toHaveBeenCalledWith('test');
  });

  it('doit mettre à jour isVoiceRecording quand onRecordingChange est appelé', () => {
    spectator.component.onRecordingChange(true);
    expect(spectator.component.isVoiceRecording).toBe(true);
    spectator.component.onRecordingChange(false);
    expect(spectator.component.isVoiceRecording).toBe(false);
  });

  it('doit logguer l\'erreur vocale quand onVoiceError est appelé', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    spectator.component.onVoiceError('Erreur micro');
    expect(consoleSpy).toHaveBeenCalled();
  });

  // ── Integration ───────────────────────────────────────────────────────────

  it('[INTÉGRATION] doit ajouter le transcript vocal au champ de saisie', () => {
    spectator.setInput('inputText', '');
    spectator.triggerEventHandler('app-voice-button', 'transcriptFinal', 'Bonjour');
    expect(spectator.component.inputText).toBe('Bonjour');
  });

  it('[INTÉGRATION] doit concaténer le transcript au texte existant', () => {
    spectator.setInput('inputText', 'Bonjour');
    spectator.triggerEventHandler('app-voice-button', 'transcriptFinal', 'le monde');
    expect(spectator.component.inputText).toBe('Bonjour le monde');
  });
});
