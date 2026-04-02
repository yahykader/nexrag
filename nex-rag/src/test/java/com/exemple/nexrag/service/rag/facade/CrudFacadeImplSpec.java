package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.dto.DeleteResponse;
import com.exemple.nexrag.dto.EmbeddingType;
import com.exemple.nexrag.exception.ResourceNotFoundException;
import com.exemple.nexrag.service.rag.ingestion.IngestionOrchestrator;
import com.exemple.nexrag.service.rag.ingestion.repository.EmbeddingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec : CrudFacadeImpl — gestion CRUD des embeddings.
 *
 * Principe SRP : chaque méthode de test couvre une opération CRUD distincte.
 * Principe DIP : EmbeddingRepository et IngestionOrchestrator sont injectés via @Mock.
 */
@DisplayName("Spec : CrudFacadeImpl — Facade de gestion CRUD des embeddings")
@ExtendWith(MockitoExtension.class)
class CrudFacadeImplSpec {

    @Mock private EmbeddingRepository   embeddingRepository;
    @Mock private IngestionOrchestrator ingestionOrchestrator;

    @InjectMocks private CrudFacadeImpl facade;

    // -------------------------------------------------------------------------
    // AC-16.1 — Suppression d'un embedding TEXT existant → DeleteResponse success
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT supprimer un embedding TEXT existant et retourner DeleteResponse success avec deletedCount=1")
    void shouldDeleteExistingTextEmbeddingAndReturnSuccessResponse() {
        when(embeddingRepository.deleteText("emb-001")).thenReturn(true);

        DeleteResponse response = facade.deleteById("emb-001", EmbeddingType.TEXT);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getDeletedCount()).isEqualTo(1);
        assertThat(response.getEmbeddingId()).isEqualTo("emb-001");
        assertThat(response.getType()).isEqualTo("text");
        verify(embeddingRepository).deleteText("emb-001");
    }

    // -------------------------------------------------------------------------
    // AC-16.2 — Embedding TEXT inexistant → ResourceNotFoundException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT lever ResourceNotFoundException pour un embeddingId TEXT inexistant")
    void shouldThrowResourceNotFoundExceptionForUnknownTextEmbedding() {
        when(embeddingRepository.deleteText("unknown-emb")).thenReturn(false);

        assertThatThrownBy(() -> facade.deleteById("unknown-emb", EmbeddingType.TEXT))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("unknown-emb");
    }

    // -------------------------------------------------------------------------
    // AC-16.3 — Batch inexistant → ResourceNotFoundException lors de deleteBatchById
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT lever ResourceNotFoundException pour un batchId inconnu lors de la suppression de batch")
    void shouldThrowResourceNotFoundExceptionForUnknownBatchId() {
        when(embeddingRepository.batchExists("batch-xyz")).thenReturn(false);

        assertThatThrownBy(() -> facade.deleteBatchById("batch-xyz"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("batch-xyz");
    }
}
