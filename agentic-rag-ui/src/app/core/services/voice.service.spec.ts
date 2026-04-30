// voice.service.spec.ts — Phase 3, User Story 6
// Vérifie enregistrement audio et transcription Whisper

import { createHttpFactory, SpectatorHttp } from '@ngneat/spectator/vitest';
import { HttpTestingController } from '@angular/common/http/testing';
import { VoiceService } from './voice.service';

// ─── Mock MediaRecorder ────────────────────────────────────────────────────

class MockMediaRecorder {
  static lastInstance: MockMediaRecorder | null = null;

  state: 'inactive' | 'recording' | 'paused' = 'inactive';
  ondataavailable: ((e: { data: Blob }) => void) | null = null;
  onstop: (() => void) | null = null;
  onerror: ((e: any) => void) | null = null;
  stream: { getTracks: () => Array<{ stop: () => void }> };

  constructor(_stream: MediaStream, _options?: any) {
    MockMediaRecorder.lastInstance = this;
    this.stream = { getTracks: () => [{ stop: vi.fn() }] };
  }

  start(_interval?: number): void {
    this.state = 'recording';
  }

  stop(): void {
    this.state = 'inactive';
    if (this.onstop) {
      this.onstop();
    }
  }
}

// ─── Suite ────────────────────────────────────────────────────────────────

describe('VoiceService', () => {
  let spectator: SpectatorHttp<VoiceService>;
  let controller: HttpTestingController;

  const createHttp = createHttpFactory(VoiceService);

  beforeEach(() => {
    MockMediaRecorder.lastInstance = null;
    vi.stubGlobal('MediaRecorder', MockMediaRecorder);
    spectator = createHttp();
    controller = spectator.controller;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    controller.verify();
  });

  // ─── US6 — AC1 ────────────────────────────────────────────────────────────

  it('doit retourner true quand getUserMedia est disponible', () => {
    vi.stubGlobal('navigator', {
      mediaDevices: {
        getUserMedia: vi.fn(),
      },
    });

    expect(spectator.service.isRecordingSupported()).toBe(true);
  });

  // ─── US6 — AC2 ────────────────────────────────────────────────────────────

  it('doit retourner false quand getUserMedia est indisponible', () => {
    vi.stubGlobal('navigator', { mediaDevices: undefined });

    expect(spectator.service.isRecordingSupported()).toBe(false);
  });

  // ─── US6 — AC3 ────────────────────────────────────────────────────────────

  it('doit démarrer l\'enregistrement et émettre true via getRecordingState', async () => {
    const mockStream = { getTracks: () => [] } as unknown as MediaStream;
    vi.stubGlobal('navigator', {
      mediaDevices: {
        getUserMedia: vi.fn().mockResolvedValue(mockStream),
      },
    });

    const states: boolean[] = [];
    spectator.service.getRecordingState().subscribe((s) => states.push(s));

    await spectator.service.startRecording();

    expect(states).toContain(true);
    expect(MockMediaRecorder.lastInstance).not.toBeNull();
    expect(MockMediaRecorder.lastInstance!.state).toBe('recording');
  });

  // ─── US6 — AC4 ────────────────────────────────────────────────────────────

  it('doit résoudre avec un Blob audio et émettre false via getRecordingState à l\'arrêt', async () => {
    const mockStream = { getTracks: () => [] } as unknown as MediaStream;
    vi.stubGlobal('navigator', {
      mediaDevices: {
        getUserMedia: vi.fn().mockResolvedValue(mockStream),
      },
    });

    const states: boolean[] = [];
    spectator.service.getRecordingState().subscribe((s) => states.push(s));

    await spectator.service.startRecording();

    const stopPromise = spectator.service.stopRecording();
    // MockMediaRecorder.stop() calls onstop synchronously
    const audioBlob = await stopPromise;

    expect(audioBlob).toBeInstanceOf(Blob);
    expect(states).toContain(false);
  });

  // ─── US6 — AC5 ────────────────────────────────────────────────────────────

  it('doit envoyer le FormData avec audio et language à POST /api/v1/voice/transcribe', () => {
    const audioBlob = new Blob(['audio-data'], { type: 'audio/webm' });
    spectator.service.transcribeWithWhisper(audioBlob, 'fr').subscribe();

    const req = controller.expectOne('/api/v1/voice/transcribe');
    expect(req.request.method).toBe('POST');

    const body: FormData = req.request.body;
    expect(body).toBeInstanceOf(FormData);

    const audioEntry = body.get('audio') as File;
    expect(audioEntry).toBeDefined();
    expect((audioEntry as File).name).toBe('recording.webm');
    expect(body.get('language')).toBe('fr');

    req.flush({
      success: true,
      transcript: 'Bonjour le monde',
      language: 'fr',
      audioSize: 10,
      filename: 'recording.webm',
    });
  });

  // ─── US6 — AC6 ────────────────────────────────────────────────────────────

  it('doit émettre l\'erreur via getErrors() quand getUserMedia rejette', async () => {
    const micError = new Error('Permission refusée');
    vi.stubGlobal('navigator', {
      mediaDevices: {
        getUserMedia: vi.fn().mockRejectedValue(micError),
      },
    });

    const errors: string[] = [];
    spectator.service.getErrors().subscribe((e) => errors.push(e));

    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await spectator.service.startRecording().catch(() => {});

    consoleSpy.mockRestore();

    expect(errors).toHaveLength(1);
    expect(errors[0]).toBe('Permission refusée');
  });
});
