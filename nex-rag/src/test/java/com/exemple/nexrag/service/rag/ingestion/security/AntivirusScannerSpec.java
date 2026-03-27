package com.exemple.nexrag.service.rag.ingestion.security;

import com.exemple.nexrag.config.ClamAvProperties;
import com.exemple.nexrag.dto.ScanResult;
import com.exemple.nexrag.exception.AntivirusScanException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : AntivirusScanner — Orchestration du scan via ClamAV")
class AntivirusScannerSpec {

    @Mock
    private ClamAvSocketClient   socketClient;

    @Mock
    private ClamAvResponseParser responseParser;

    private ClamAvProperties  props;
    private AntivirusScanner  scanner;

    @BeforeEach
    void setUp() {
        props = new ClamAvProperties();
        props.setEnabled(true);
        props.setHost("localhost");
        props.setPort(3310);
        props.setTimeout(5000);
        props.setMaxFileSize(100L * 1024 * 1024);
        props.setChunkSize(8192);

        scanner = new AntivirusScanner(props, socketClient, responseParser);
    }

    @Test
    @DisplayName("DOIT retourner ScanResult CLEAN quand ClamAV répond OK")
    void devraitRetournerCleanQuandReponsOk() throws Exception {
        when(socketClient.sendInstream(any(InputStream.class), anyLong()))
            .thenReturn("stream: OK");
        when(responseParser.parse(anyString(), eq("stream: OK")))
            .thenReturn(ScanResult.clean("test.pdf"));

        ScanResult result = scanner.scanBytes("contenu".getBytes(), "test.pdf");

        assertThat(result.isClean()).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner ScanResult INFECTED quand ClamAV répond FOUND")
    void devraitRetournerInfectedQuandReponseFound() throws Exception {
        when(socketClient.sendInstream(any(InputStream.class), anyLong()))
            .thenReturn("stream: Eicar-Test-Signature FOUND");
        when(responseParser.parse(anyString(), anyString()))
            .thenReturn(ScanResult.infected("malware.exe", "Eicar-Test-Signature"));

        ScanResult result = scanner.scanBytes("eicar".getBytes(), "malware.exe");

        assertThat(result.isInfected()).isTrue();
        assertThat(result.getVirusName()).isEqualTo("Eicar-Test-Signature");
    }

    @Test
    @DisplayName("DOIT lever AntivirusScanException quand les données scannées sont null")
    void devraitLeverExceptionPourDonneesNull() {
        assertThatThrownBy(() -> scanner.scanBytes(null, "test.pdf"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DOIT retourner CLEAN sans appel socket quand le scan est désactivé")
    void devraitRetournerCleanSansScanQuandDesactive() throws AntivirusScanException {
        props.setEnabled(false);

        ScanResult result = scanner.scanBytes("data".getBytes(), "fichier.txt");

        assertThat(result.isClean()).isTrue();
        verifyNoInteractions(socketClient);
    }

    @Test
    @DisplayName("DOIT lever AntivirusScanException quand le fichier dépasse maxFileSize")
    void devraitLeverExceptionFichierTropGrand() {
        props.setMaxFileSize(5L); // 5 bytes max
        byte[] trop_grand = new byte[10];

        assertThatThrownBy(() -> scanner.scanBytes(trop_grand, "gros.pdf"))
            .isInstanceOf(AntivirusScanException.class)
            .hasMessageContaining("volumineux");
    }

    @Test
    @DisplayName("DOIT retourner true pour isAvailable() quand ClamAV répond au ping")
    void devraitRetournerTruePourIsAvailableQuandPingOk() {
        when(socketClient.ping()).thenReturn(true);
        assertThat(scanner.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false pour isAvailable() quand ClamAV ne répond pas")
    void devraitRetournerFalsePourIsAvailableQuandPingKo() {
        when(socketClient.ping()).thenReturn(false);
        assertThat(scanner.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("DOIT déléguer le scan à socketClient.sendInstream")
    void devraitDeleguerScanASocketClient() throws Exception {
        when(socketClient.sendInstream(any(InputStream.class), anyLong()))
            .thenReturn("stream: OK");
        when(responseParser.parse(anyString(), anyString()))
            .thenReturn(ScanResult.clean("doc.pdf"));

        scanner.scanBytes("contenu".getBytes(), "doc.pdf");

        verify(socketClient).sendInstream(any(InputStream.class), anyLong());
    }
}
