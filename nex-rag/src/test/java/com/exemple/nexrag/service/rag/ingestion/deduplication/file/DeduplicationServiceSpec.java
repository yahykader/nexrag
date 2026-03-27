package com.exemple.nexrag.service.rag.ingestion.deduplication.file;

import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : DeduplicationService — Orchestration déduplication fichiers")
class DeduplicationServiceSpec {

    @Mock
    private DeduplicationStore store;

    @Mock
    private HashComputer hashComputer;

    @Mock
    private RAGMetrics ragMetrics;

    @InjectMocks
    private DeduplicationService service;

    @Test
    @DisplayName("DOIT détecter un doublon quand le hash existe dans le store")
    void devraitDetecterDoublonQuandHashExistant() {
        when(hashComputer.toShortHash(anyString())).thenReturn("abc...");
        when(store.exists("hashExistant")).thenReturn(true);

        assertThat(service.isDuplicateByHash("hashExistant")).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false quand le hash est absent du store")
    void devraitRetournerFalseQuandHashAbsent() {
        when(store.exists("hashNouveau")).thenReturn(false);

        assertThat(service.isDuplicateByHash("hashNouveau")).isFalse();
    }

    @Test
    @DisplayName("DOIT enregistrer la métrique de doublon via ragMetrics quand doublon détecté")
    void devraitEnregistrerMetriqueDoublonQuandDetecte() {
        when(hashComputer.toShortHash(anyString())).thenReturn("abc...");
        when(store.exists(anyString())).thenReturn(true);

        service.isDuplicateAndRecord("hashExistant", "PDF");

        verify(ragMetrics).recordDuplicate("PDF");
    }

    @Test
    @DisplayName("DOIT utiliser 'unknown' comme stratégie si le nom est null")
    void devraitUtiliserUnknownSiStrategieNull() {
        when(hashComputer.toShortHash(anyString())).thenReturn("abc...");
        when(store.exists(anyString())).thenReturn(true);

        service.isDuplicateAndRecord("hash", null);

        verify(ragMetrics).recordDuplicate("unknown");
    }

    @Test
    @DisplayName("DOIT déléguer le calcul de hash à HashComputer pour computeHash(byte[])")
    void devraitDeleguerCalculHashAHashComputer() {
        byte[] content = "contenu".getBytes();
        when(hashComputer.compute(content)).thenReturn("hashCalcule");

        assertThat(service.computeHash(content)).isEqualTo("hashCalcule");
        verify(hashComputer).compute(content);
    }

    @Test
    @DisplayName("DOIT marquer un fichier comme ingéré via store.save() avec batchId")
    void devraitMarquerFichierCommeIngere() {
        service.markAsIngested("monHash", "batch-001");

        verify(store).save(eq("monHash"), eq("batch-001"), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("DOIT ne pas appeler recordDuplicate quand le fichier n'est pas un doublon")
    void devraitNePasEnregistrerMetriqueQuandPasDoublon() {
        when(store.exists(anyString())).thenReturn(false);

        service.isDuplicateAndRecord("hashNouveau", "DOCX");

        verify(ragMetrics, never()).recordDuplicate(anyString());
    }

    @Test
    @DisplayName("DOIT retourner false pour isHealthy() quand Redis est inaccessible")
    void devraitRetournerFalsePourIsHealthyQuandRedisInaccessible() {
        when(store.isAvailable()).thenReturn(false);

        assertThat(service.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("DOIT calculer le hash d'un MultipartFile via hashComputer.compute(file)")
    void devraitCalculerHashMultipartFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "file", "doc.pdf", "application/pdf", "contenu".getBytes());
        when(hashComputer.compute(file)).thenReturn("hashFichier");

        assertThat(service.computeHash(file)).isEqualTo("hashFichier");
    }
}
