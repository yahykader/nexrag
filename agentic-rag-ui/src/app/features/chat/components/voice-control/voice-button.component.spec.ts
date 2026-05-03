import { createComponentFactory, mockProvider, Spectator, SpyObject } from '@ngneat/spectator/vitest';
import { of, EMPTY, throwError } from 'rxjs';
import { VoiceButtonComponent } from './voice-button.component';
import { VoiceService } from '../../../../core/services/voice.service';

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('VoiceButtonComponent', () => {
  let spectator: Spectator<VoiceButtonComponent>;
  let voiceService: SpyObject<VoiceService>;

  const createComponent = createComponentFactory({
    component: VoiceButtonComponent,
    providers: [mockProvider(VoiceService)],
    detectChanges: false,
  });

  const defaultMocks = (svc: SpyObject<VoiceService>) => {
    svc.isRecordingSupported.mockReturnValue(true);
    svc.startRecording.mockResolvedValue(undefined);
    svc.stopRecording.mockResolvedValue(new Blob(['audio'], { type: 'audio/webm' }));
    svc.transcribeWithWhisper.mockReturnValue(of({
      success: true,
      transcript: 'Bonjour',
      language: 'fr',
      audioSize: 100,
      filename: 'audio.webm',
    }));
    svc.getErrors.mockReturnValue(EMPTY);
  };

  beforeEach(() => {
    spectator = createComponent();
    voiceService = spectator.inject(VoiceService);
    defaultMocks(voiceService);
    spectator.detectChanges();
  });

  afterEach(() => vi.restoreAllMocks());

  // ── Smoke ─────────────────────────────────────────────────────────────────

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  // ── Support detection ─────────────────────────────────────────────────────

  it('doit désactiver le bouton si le microphone n\'est pas supporté', () => {
    spectator = createComponent();
    voiceService = spectator.inject(VoiceService);
    defaultMocks(voiceService);
    voiceService.isRecordingSupported.mockReturnValue(false);
    spectator.detectChanges();
    expect(spectator.component.isSupported).toBe(false);
  });

  // ── Start recording ───────────────────────────────────────────────────────

  it('doit appeler startRecording() au click et émettre recordingChange=true', async () => {
    const recordingChangeSpy = vi.fn();
    spectator.output('recordingChange').subscribe(recordingChangeSpy);
    await spectator.component.startRecording();
    expect(voiceService.startRecording).toHaveBeenCalled();
    expect(recordingChangeSpy).toHaveBeenCalledWith(true);
  });

  // ── Recording state class ─────────────────────────────────────────────────

  it('doit appliquer la classe btn-danger pendant l\'enregistrement', async () => {
    await spectator.component.startRecording();
    expect(spectator.component.getButtonClass()).toBe('btn-danger');
  });

  // ── Stop + transcribe ─────────────────────────────────────────────────────

  it('doit appeler stopRecording() puis transcribeWithWhisper() à l\'arrêt', async () => {
    await spectator.component.startRecording();
    await spectator.component.stopRecording();
    expect(voiceService.stopRecording).toHaveBeenCalled();
    expect(voiceService.transcribeWithWhisper).toHaveBeenCalledWith(expect.any(Blob), 'fr');
  });

  // ── Transcript emit ───────────────────────────────────────────────────────

  it('doit émettre transcriptFinal après une transcription réussie', async () => {
    const transcriptSpy = vi.fn();
    spectator.output('transcriptFinal').subscribe(transcriptSpy);
    await spectator.component.startRecording();
    await spectator.component.stopRecording();
    expect(transcriptSpy).toHaveBeenCalledWith('Bonjour');
  });

  // ── Error handling ────────────────────────────────────────────────────────

  it('doit émettre error si VoiceService.getErrors() émet une erreur', () => {
    spectator = createComponent();
    voiceService = spectator.inject(VoiceService);
    defaultMocks(voiceService);
    voiceService.getErrors.mockReturnValue(of('Microphone non disponible'));
    const errorSpy = vi.spyOn(spectator.component.error, 'emit');
    spectator.detectChanges();
    expect(errorSpy).toHaveBeenCalledWith('Microphone non disponible');
    expect(spectator.component.errorMessage).toBe('Microphone non disponible');
  });

  // ── Tooltip states ────────────────────────────────────────────────────────

  it('doit retourner le tooltip correct selon l\'état', async () => {
    expect(spectator.component.getTooltip()).toBe('Démarrer l\'enregistrement vocal');
    await spectator.component.startRecording();
    expect(spectator.component.getTooltip()).toBe('Arrêter l\'enregistrement');
  });

  // ── stopRecognition ───────────────────────────────────────────────────────

  it('doit appeler stopRecording() via stopRecognition() quand isRecording=true', async () => {
    await spectator.component.startRecording();
    const stopSpy = vi.spyOn(spectator.component, 'stopRecording').mockResolvedValue();
    spectator.component.stopRecognition();
    expect(stopSpy).toHaveBeenCalled();
  });

  // ── startRecording error ──────────────────────────────────────────────────

  it('doit gérer l\'erreur si startRecording() échoue', async () => {
    voiceService.startRecording.mockRejectedValue(new Error('Microphone inaccessible'));
    const errorSpy = vi.spyOn(spectator.component.error, 'emit');
    await spectator.component.startRecording();
    expect(errorSpy).toHaveBeenCalledWith('Microphone inaccessible');
  });

  // ── Failed transcription ──────────────────────────────────────────────────

  it('doit gérer l\'erreur quand la transcription échoue (success=false)', async () => {
    voiceService.transcribeWithWhisper.mockReturnValue(of({
      success: false,
      transcript: '',
      language: 'fr',
      audioSize: 0,
      filename: '',
      error: 'Aucune parole détectée',
    }));
    const errorSpy = vi.spyOn(spectator.component.error, 'emit');
    await spectator.component.startRecording();
    await spectator.component.stopRecording();
    expect(errorSpy).toHaveBeenCalledWith('Aucune parole détectée');
  });

  // ── transcribeWithWhisper HTTP error ──────────────────────────────────────

  it('doit gérer l\'erreur HTTP de transcription', async () => {
    voiceService.transcribeWithWhisper.mockReturnValue(
      throwError(() => ({ message: 'HTTP 500', error: { error: '' } }))
    );
    const errorSpy = vi.spyOn(spectator.component.error, 'emit');
    await spectator.component.startRecording();
    await spectator.component.stopRecording();
    expect(errorSpy).toHaveBeenCalled();
  });

  // ── Duration timer ────────────────────────────────────────────────────────

  it('doit mettre à jour recordingDuration pendant l\'enregistrement', async () => {
    vi.useFakeTimers();
    try {
      await spectator.component.startRecording();
      vi.advanceTimersByTime(2000);
      spectator.detectChanges();
      expect(spectator.component.recordingDuration).toBe('00:02');
    } finally {
      vi.useRealTimers();
    }
  });

  // ── Integration ───────────────────────────────────────────────────────────

  it('[INTÉGRATION] le flux complet : démarrage → arrêt → transcript', async () => {
    const recordingChangeSpy = vi.fn();
    const transcriptSpy = vi.fn();
    spectator.output('recordingChange').subscribe(recordingChangeSpy);
    spectator.output('transcriptFinal').subscribe(transcriptSpy);

    await spectator.component.toggleRecording();
    expect(recordingChangeSpy).toHaveBeenCalledWith(true);

    await spectator.component.toggleRecording();
    expect(recordingChangeSpy).toHaveBeenCalledWith(false);
    expect(transcriptSpy).toHaveBeenCalledWith('Bonjour');
  });
});
