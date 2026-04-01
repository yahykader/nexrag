package com.exemple.nexrag.service.rag.voice;

import com.exemple.nexrag.config.WhisperProperties;
import com.theokanning.openai.service.OpenAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : WhisperService — Validation et disponibilité du service Whisper")
class WhisperServiceSpec {

    @Mock
    private WhisperProperties props;

    @Mock
    private AudioTempFile audioTempFile;

    @InjectMocks
    private WhisperService service;

    @BeforeEach
    void setUp() {
        // @Value("${openai.api.key}") non injecté par Mockito — ReflectionTestUtils requis
        ReflectionTestUtils.setField(service, "apiKey", "test-key-fake");
        lenient().when(props.getMinAudioBytes()).thenReturn(1_000);
    }

    // US-1 / AC-13.2
    @Test
    @DisplayName("DOIT lever IllegalArgumentException quand l'audio est null")
    void devraitLeverIllegalArgumentExceptionPourAudioNull() {
        assertThatThrownBy(() -> service.transcribeAudio(null, "test.wav", "fr"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Données audio vides ou absentes");
    }

    // US-1 / AC-13.2
    @Test
    @DisplayName("DOIT lever IllegalArgumentException quand l'audio est vide (0 bytes)")
    void devraitLeverIllegalArgumentExceptionPourAudioVide() {
        assertThatThrownBy(() -> service.transcribeAudio(new byte[0], "test.wav", "fr"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Données audio vides ou absentes");
    }

    // US-1 / FR-001
    @Test
    @DisplayName("DOIT lever IllegalArgumentException quand la taille dépasse 25 MB")
    void devraitLeverIllegalArgumentExceptionSiTailleDepasse25Mo() {
        byte[] tooBig = new byte[26_214_401]; // 25 MB + 1 byte

        assertThatThrownBy(() -> service.transcribeAudio(tooBig, "gros.wav", "fr"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("25 MB");
    }

    // US-1 / AC-13.3
    @Test
    @DisplayName("DOIT retourner false si openAiService n'est pas initialisé")
    void devraitRetournerFalseSiOpenAiServiceNonInitialise() {
        // Après @InjectMocks, @PostConstruct n'est pas appelé → openAiService == null
        assertThat(service.isAvailable()).isFalse();
    }

    // US-1 / AC-13.3
    @Test
    @DisplayName("DOIT retourner true si apiKey et openAiService sont configurés")
    void devraitRetournerTrueSiApiKeyConfigureEtServiceInitialise() {
        OpenAiService mockOpenAi = mock(OpenAiService.class);
        ReflectionTestUtils.setField(service, "openAiService", mockOpenAi);

        assertThat(service.isAvailable()).isTrue();
    }

    // US-1 / FR-001
    @Test
    @DisplayName("DOIT ne pas appeler audioTempFile si l'audio est invalide")
    void devraitNePasAppelerAudioTempFilePourAudioInvalide() {
        assertThatThrownBy(() -> service.transcribeAudio(new byte[0], "test.wav", "fr"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(audioTempFile);
    }

    // US-1 / FR-001
    @Test
    @DisplayName("DOIT ne pas appeler audioTempFile si la taille dépasse 25 MB")
    void devraitNePasAppelerAudioTempFilePourTailleDepassee() {
        byte[] tooBig = new byte[26_214_401];

        assertThatThrownBy(() -> service.transcribeAudio(tooBig, "gros.wav", "fr"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(audioTempFile);
    }
}
