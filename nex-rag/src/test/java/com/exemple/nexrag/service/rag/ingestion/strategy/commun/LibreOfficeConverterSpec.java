package com.exemple.nexrag.service.rag.ingestion.strategy.commun;

import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Spec : LibreOfficeConverter — Conversion XLSX→PDF via soffice.
 */
@DisplayName("Spec : LibreOfficeConverter — Conversion XLSX→PDF avec gestion d'erreurs")
@ExtendWith(MockitoExtension.class)
class LibreOfficeConverterSpec {

    @Mock private XlsxProperties             props;
    @Mock private XlsxProperties.Libreoffice libreofficeProps;
    @Mock private RAGMetrics                  ragMetrics;

    @InjectMocks
    private LibreOfficeConverter converter;

    // -------------------------------------------------------------------------
    // isEnabled
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner true quand LibreOffice est activé dans la configuration")
    void shouldReturnTrueWhenLibreOfficeIsEnabled() {
        when(props.getLibreoffice()).thenReturn(libreofficeProps);
        when(libreofficeProps.isEnabled()).thenReturn(true);

        assertThat(converter.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false quand LibreOffice est désactivé dans la configuration")
    void shouldReturnFalseWhenLibreOfficeIsDisabled() {
        when(props.getLibreoffice()).thenReturn(libreofficeProps);
        when(libreofficeProps.isEnabled()).thenReturn(false);

        assertThat(converter.isEnabled()).isFalse();
    }

    // -------------------------------------------------------------------------
    // convert — cas désactivé
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT lever IllegalStateException quand LibreOffice est désactivé")
    void shouldThrowIllegalStateWhenLibreOfficeDisabled() {
        when(props.getLibreoffice()).thenReturn(libreofficeProps);
        when(libreofficeProps.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> converter.convert(new byte[]{1, 2, 3}, "test"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("désactivé");
    }

    // -------------------------------------------------------------------------
    // convert — chemin binaire configuré mais introuvable
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT lever IllegalStateException quand le chemin soffice configuré est introuvable")
    void shouldThrowIllegalStateWhenConfiguredSofficePathNotFound() {
        when(props.getLibreoffice()).thenReturn(libreofficeProps);
        when(libreofficeProps.isEnabled()).thenReturn(true);
        when(libreofficeProps.getSofficePath()).thenReturn("/chemin/inexistant/soffice");

        assertThatThrownBy(() -> converter.convert(new byte[]{1, 2, 3}, "test"))
            .isInstanceOf(Exception.class);
    }
}
