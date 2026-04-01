package com.exemple.nexrag.service.rag.voice;

import com.exemple.nexrag.config.WhisperProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : AudioTempFile — Cycle de vie du fichier audio temporaire")
class AudioTempFileSpec {

    @Mock
    private WhisperProperties props;

    @InjectMocks
    private AudioTempFile audioTempFile;

    private final List<File> createdFiles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(props.getDefaultExtension()).thenReturn(".webm");
    }

    @AfterEach
    void tearDown() {
        createdFiles.forEach(f -> { if (f != null && f.exists()) f.delete(); });
        createdFiles.clear();
    }

    // US-1 / AC-13.4
    @Test
    @DisplayName("DOIT créer un fichier temporaire avec l'extension du nom original")
    void devraitCreerFichierTempAvecExtensionDuNomOriginal() throws IOException {
        byte[] bytes = "audio content".getBytes();

        File file = audioTempFile.create(bytes, "audio.mp3");
        createdFiles.add(file);

        assertThat(file).exists();
        assertThat(file.getName()).endsWith(".mp3");
    }

    // US-1 / AC-13.4
    @Test
    @DisplayName("DOIT utiliser l'extension par défaut si le nom de fichier n'a pas d'extension")
    void devraitUtiliserExtensionParDefautSiExtensionAbsente() throws IOException {
        byte[] bytes = "audio content".getBytes();

        File file = audioTempFile.create(bytes, "audio");
        createdFiles.add(file);

        assertThat(file.getName()).endsWith(".webm");
    }

    // US-1 / AC-13.4
    @Test
    @DisplayName("DOIT utiliser l'extension par défaut si le nom de fichier est null")
    void devraitUtiliserExtensionParDefautSiNomNull() throws IOException {
        byte[] bytes = "audio content".getBytes();

        File file = audioTempFile.create(bytes, null);
        createdFiles.add(file);

        assertThat(file.getName()).endsWith(".webm");
    }

    // US-1 / AC-13.4
    @Test
    @DisplayName("DOIT écrire correctement les bytes dans le fichier temporaire")
    void devraitEcrireLesBytesCorrectementDansFichierTemp() throws IOException {
        byte[] bytes = "contenu audio test".getBytes();

        File file = audioTempFile.create(bytes, "test.wav");
        createdFiles.add(file);

        byte[] written = Files.readAllBytes(file.toPath());
        assertThat(written).isEqualTo(bytes);
    }

    // US-1 / AC-13.5
    @Test
    @DisplayName("DOIT supprimer le fichier temporaire silencieusement")
    void devraitSupprimerFichierSilencieusement() throws IOException {
        File file = audioTempFile.create("données".getBytes(), "audio.webm");

        assertThat(file).exists();
        audioTempFile.deleteSilently(file);

        assertThat(file).doesNotExist();
    }

    // US-1 / AC-13.5
    @Test
    @DisplayName("DOIT ne pas lever d'exception si null est passé à deleteSilently")
    void devraitNePasLeverExceptionSiNullPasseADeleteSilently() {
        assertThatCode(() -> audioTempFile.deleteSilently(null))
            .doesNotThrowAnyException();
    }
}
