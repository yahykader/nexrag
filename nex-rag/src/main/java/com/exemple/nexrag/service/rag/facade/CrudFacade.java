package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Facade exposée au controller CRUD.
 *
 * Principe ISP : interface fine → le controller ne dépend que de ce dont il a besoin.
 * Principe DIP : le controller dépend de cette abstraction, pas des services concrets.
 *
 * @author ayahyaoui
 * @version 1.0
 */
public interface CrudFacade {

    // -------------------------------------------------------------------------
    // Suppressions individuelles
    // -------------------------------------------------------------------------

    DeleteResponse deleteById(String embeddingId, EmbeddingType type);

    DeleteResponse deleteBatch(List<String> ids, EmbeddingType type);

    // -------------------------------------------------------------------------
    // Suppressions par batch métier
    // -------------------------------------------------------------------------

    DeleteResponse deleteBatchById(String batchId);

    DeleteResponse deleteAll(String confirmation);

    // -------------------------------------------------------------------------
    // Lecture / Vérification
    // -------------------------------------------------------------------------

    DuplicateCheckResponse checkDuplicate(MultipartFile file);

    BatchInfoResponse getBatchInfo(String batchId);

    SystemStatsResponse getSystemStats();
}