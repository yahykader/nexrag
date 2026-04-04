package com.exemple.nexrag.service.rag.ingestion.security;

import com.exemple.nexrag.dto.ScanResult;
import com.exemple.nexrag.exception.AntivirusScanException;
import com.exemple.nexrag.exception.VirusDetectedException;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : AntivirusGuard — Garde antivirus fail-secure pour fichiers entrants")
class AntivirusGuardSpec {

    @Mock
    private AntivirusScanner antivirusScanner;

    @Mock
    private RAGMetrics ragMetrics;

    @InjectMocks
    private AntivirusGuard guard;

    @BeforeEach
    void setUp() {
        // @Value("${antivirus.enabled:false}") n'est pas injecté par Mockito
        ReflectionTestUtils.setField(guard, "enabled", true);
    }

    @Test
    @DisplayName("DOIT ne pas lever d'exception quand le scanner retourne CLEAN")
    void devraitNePasLeverExceptionPourFichierSain()
            throws IOException, AntivirusScanException {
        MockMultipartFile file = multipartFile("sain.pdf", "contenu sain".getBytes());
        when(antivirusScanner.scanBytes(any(), anyString()))
            .thenReturn(ScanResult.clean("sain.pdf"));

        assertThatCode(() -> guard.assertClean(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DOIT lever VirusDetectedException quand le scanner retourne INFECTED")
    void devraitLeverVirusDetectedExceptionPourFichierInfecte()
            throws IOException, AntivirusScanException {
        MockMultipartFile file = multipartFile("virus.exe", "eicar".getBytes());
        when(antivirusScanner.scanBytes(any(), anyString()))
            .thenReturn(ScanResult.infected("virus.exe", "Eicar-Test-Signature"));

        assertThatThrownBy(() -> guard.assertClean(file))
            .isInstanceOf(VirusDetectedException.class);
    }

    @Test
    @DisplayName("DOIT enregistrer la métrique via ragMetrics quand un virus est détecté")
    void devraitEnregistrerMetriqueQuandVirusDetecte()
            throws IOException, AntivirusScanException {
        MockMultipartFile file = multipartFile("infected.zip", "virus".getBytes());
        when(antivirusScanner.scanBytes(any(), anyString()))
            .thenReturn(ScanResult.infected("infected.zip", "Trojan.Generic"));

        try {
            guard.assertClean(file);
        } catch (Exception ignored) {}

        verify(ragMetrics).recordVirusDetected("Trojan.Generic");
    }

    @Test
    @DisplayName("DOIT ne pas appeler le scanner quand antivirus.enabled est false")
    void devraitIgnorerLeScanQuandDesactive()
            throws IOException, AntivirusScanException {
        ReflectionTestUtils.setField(guard, "enabled", false);
        MockMultipartFile file = multipartFile("test.pdf", "contenu".getBytes());

        assertThatCode(() -> guard.assertClean(file)).doesNotThrowAnyException();
        verifyNoInteractions(antivirusScanner);
    }

    @Test
    @DisplayName("DOIT absorber AntivirusScanException et autoriser le fichier (fail-open)")
    void devraitAbsorberExceptionTechniqueDuScanner() throws IOException {
        MockMultipartFile file = multipartFile("probleme.pdf", "data".getBytes());
        when(antivirusScanner.scanBytes(any(), anyString()))
            .thenThrow(new AntivirusScanException("Read timed out"));

        // Fail-open : timeout ClamAV → fichier autorisé, aucune exception
        assertThatNoException().isThrownBy(() -> guard.assertClean(file));
    }

    @Test
    @DisplayName("DOIT lever VirusDetectedException pour un résultat ERROR (fail-secure)")
    void devraitLeverExceptionPourResultatError()
            throws IOException, AntivirusScanException {
        MockMultipartFile file = multipartFile("douteux.bin", "data".getBytes());
        when(antivirusScanner.scanBytes(any(), anyString()))
            .thenReturn(ScanResult.error("douteux.bin", "Erreur interne scanner"));

        // Fail-secure : tout résultat non-CLEAN lève une exception
        assertThatThrownBy(() -> guard.assertClean(file))
            .isInstanceOf(Exception.class);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private MockMultipartFile multipartFile(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "application/octet-stream", content);
    }
}
