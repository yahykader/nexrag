package com.exemple.nexrag.service.rag.voice;

import com.exemple.nexrag.config.WhisperProperties;
import com.exemple.nexrag.service.rag.voice.AudioTempFile;
import com.theokanning.openai.audio.CreateTranscriptionRequest;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.time.Duration;

/**
 * Service de transcription audio via OpenAI Whisper.
 *
 * Principe SRP  : unique responsabilité → orchestrer la transcription.
 *                 La gestion des fichiers temporaires est dans {@link AudioTempFile}.
 *                 La configuration est dans {@link WhisperProperties}.
 * Principe DIP  : dépend des abstractions {@link AudioTempFile} et
 *                 {@link WhisperProperties}.
 * Clean code    : la clé API est partagée avec les autres services OpenAI
 *                 via {@code openai.api.key} — pas de duplication de config.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhisperService {

    private final WhisperProperties props;
    private final AudioTempFile     audioTempFile;

    @Value("${openai.api.key}")
    private String apiKey;

    private OpenAiService openAiService;

    @PostConstruct
    void init() {
        this.openAiService = new OpenAiService(
            apiKey,
            Duration.ofSeconds(props.getTimeoutSeconds())
        );
        log.info("✅ [Whisper] Service initialisé (timeout={}s, model={})",
            props.getTimeoutSeconds(), props.getModel());
    }

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    /**
     * Transcrit un fichier audio en texte via Whisper.
     *
     * @param audioBytes       contenu audio brut
     * @param originalFilename nom du fichier (pour l'extension)
     * @param language         code langue ISO-639-1 (ex : "fr", "en")
     * @return texte transcrit, jamais vide
     * @throws IllegalArgumentException si les données audio sont invalides
     * @throws RuntimeException         si l'appel Whisper échoue
     */
    public String transcribeAudio(byte[] audioBytes, String originalFilename, String language) {
        validateAudio(audioBytes);

        File tempFile = null;
        try {
            tempFile = audioTempFile.create(audioBytes, originalFilename);

            log.info("🎤 [Whisper] Transcription : {} ({} bytes) — langue : {}",
                originalFilename, audioBytes.length, language);

            String transcript = callWhisperApi(tempFile, language);

            return validateTranscript(transcript);

        } catch (IOException e) {
            log.error("❌ [Whisper] Erreur fichier temporaire : {}", e.getMessage(), e);
            throw new RuntimeException("Erreur lors de la création du fichier audio temporaire", e);
        } finally {
            audioTempFile.deleteSilently(tempFile);
        }
    }

    /**
     * Indique si le service est prêt à traiter des requêtes.
     */
    public boolean isAvailable() {
        return openAiService != null
            && apiKey != null
            && !apiKey.isBlank();
    }

    // -------------------------------------------------------------------------
    // Privé — étapes de la transcription
    // -------------------------------------------------------------------------

    private void validateAudio(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("Données audio vides ou absentes");
        }
        if (audioBytes.length < props.getMinAudioBytes()) {
            log.warn("⚠️ [Whisper] Audio très court : {} bytes — possible silence",
                audioBytes.length);
        }
    }

    private String callWhisperApi(File tempFile, String language) {
        CreateTranscriptionRequest request = CreateTranscriptionRequest.builder()
            .model(props.getModel())
            .language(language)
            .build();

        long   start      = System.currentTimeMillis();
        String transcript = openAiService
            .createTranscription(request, tempFile.getPath())
            .getText();
        long   duration   = System.currentTimeMillis() - start;

        log.info("⏱️ [Whisper] Appel API : {}ms", duration);
        log.debug("📝 [Whisper] Transcription brute : '{}'", transcript);

        return transcript;
    }

    private String validateTranscript(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            log.warn("⚠️ [Whisper] Transcription vide — silence ou audio non reconnu");
            throw new RuntimeException(
                "Aucune transcription reçue — vérifiez que l'audio contient de la parole"
            );
        }

        String result = transcript.trim();
        log.info("✅ [Whisper] Transcription réussie — {} caractères : '{}'",
            result.length(),
            result.length() > 100 ? result.substring(0, 100) + "..." : result);

        return result;
    }
}