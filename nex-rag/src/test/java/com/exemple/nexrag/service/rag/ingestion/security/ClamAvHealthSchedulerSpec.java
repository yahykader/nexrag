package com.exemple.nexrag.service.rag.ingestion.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : ClamAvHealthScheduler — Surveillance périodique de ClamAV")
class ClamAvHealthSchedulerSpec {

    @Mock
    private AntivirusScanner antivirusScanner;

    @InjectMocks
    private ClamAvHealthScheduler scheduler;

    @Test
    @DisplayName("DOIT ne pas lever d'exception quand ClamAV est disponible")
    void devraitNePasLeverExceptionQuandClamAvDisponible() {
        when(antivirusScanner.isAvailable()).thenReturn(true);
        assertThatCode(() -> scheduler.checkHealth()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DOIT ne pas lever d'exception quand ClamAV est indisponible")
    void devraitNePasLeverExceptionQuandClamAvIndisponible() {
        when(antivirusScanner.isAvailable()).thenReturn(false);
        assertThatCode(() -> scheduler.checkHealth()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DOIT ne pas lever d'exception quand isAvailable() lève une RuntimeException")
    void devraitAbsorberExceptionRuntimeDuScanner() {
        when(antivirusScanner.isAvailable())
            .thenThrow(new RuntimeException("connexion refusée"));

        assertThatCode(() -> scheduler.checkHealth()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DOIT invoquer antivirusScanner.isAvailable() à chaque appel à checkHealth()")
    void devraitInvoquerIsAvailableAChaqueScan() {
        when(antivirusScanner.isAvailable()).thenReturn(true);

        scheduler.checkHealth();
        scheduler.checkHealth();

        verify(antivirusScanner, org.mockito.Mockito.times(2)).isAvailable();
    }
}
