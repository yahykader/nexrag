package com.exemple.nexrag.service.rag.metrics.embedding;

import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : OpenAiEmbeddingService — Latence et erreurs des appels embedding")
class OpenAiEmbeddingServiceSpec {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private RAGMetrics ragMetrics;

    @InjectMocks
    private OpenAiEmbeddingService service;

    private Embedding stubEmbedding;
    private Response<Embedding> stubResponse;

    @BeforeEach
    void setUp() {
        stubEmbedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
        stubResponse  = Response.from(stubEmbedding);
    }

    // US-3 / AC-14.4 — succès embedText
    @Test
    @DisplayName("DOIT enregistrer recordApiCall après un embedding de texte réussi")
    void devraitEnregistrerAppelAPIApresEmbeddingTexteReussi() {
        // embed(String) est une méthode default sur l'interface → stubber directement la surcharge String
        when(embeddingModel.embed(anyString())).thenReturn(stubResponse);

        service.embedText("Bonjour monde");

        verify(ragMetrics).recordApiCall(eq("embed_text"), anyLong());
        verify(ragMetrics, never()).recordApiError(any());
    }

    // US-3 / AC-14.4 — échec embedText
    @Test
    @DisplayName("DOIT enregistrer recordApiError et propager RuntimeException en cas d'échec embedding texte")
    void devraitEnregistrerErreurAPIApresEchecEmbeddingTexte() {
        when(embeddingModel.embed(anyString()))
            .thenThrow(new RuntimeException("API indisponible"));

        assertThatThrownBy(() -> service.embedText("Bonjour"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Embedding échoué");

        verify(ragMetrics).recordApiError("embed_text");
    }

    // US-3 / AC-14.4 — guard: pas de succès en cas d'échec
    @Test
    @DisplayName("DOIT ne pas appeler recordApiCall quand l'embedding échoue")
    void devraitNePasEnregistrerSuccesEnCasDechecEmbeddingTexte() {
        when(embeddingModel.embed(anyString()))
            .thenThrow(new RuntimeException("Timeout"));

        assertThatThrownBy(() -> service.embedText("test"))
            .isInstanceOf(RuntimeException.class);

        verify(ragMetrics, never()).recordApiCall(any(), anyLong());
    }

    // US-3 / AC-14.4 — embedSegment
    @Test
    @DisplayName("DOIT enregistrer recordApiCall après un embedding de segment réussi")
    void devraitEnregistrerAppelAPIApresEmbeddingSegment() {
        when(embeddingModel.embed(any(TextSegment.class))).thenReturn(stubResponse);

        service.embedSegment(TextSegment.from("segment de test"));

        verify(ragMetrics).recordApiCall(eq("embed_segment"), anyLong());
        verify(ragMetrics, never()).recordApiError(any());
    }

    // US-3 / AC-14.4 — embedBatch
    @Test
    @DisplayName("DOIT enregistrer recordApiCall après un embedding en batch réussi")
    void devraitEnregistrerAppelAPIApresEmbeddingBatch() {
        Response<List<Embedding>> batchResponse = Response.from(List.of(stubEmbedding, stubEmbedding));
        when(embeddingModel.embedAll(anyList())).thenReturn(batchResponse);

        service.embedBatch(List.of("texte un", "texte deux"));

        verify(ragMetrics).recordApiCall(eq("embed_batch"), anyLong());
        verify(ragMetrics, never()).recordApiError(any());
    }
}
