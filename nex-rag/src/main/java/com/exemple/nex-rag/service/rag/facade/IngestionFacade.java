package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Facade exposée au controller d'ingestion.
 *
 * Principe ISP : interface fine → le controller ne dépend que de ce contrat.
 * Principe DIP : le controller dépend de cette abstraction, pas des services concrets.
 *
 * @author ayahyaoui
 * @version 1.0
 */
public interface IngestionFacade {

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    IngestionResponse        uploadSync(MultipartFile file, String batchId);

    AsyncResponse            uploadAsync(MultipartFile file, String batchId);

    BatchResponse            uploadBatch(List<MultipartFile> files, String batchId);

    BatchDetailedResponse    uploadBatchDetailed(List<MultipartFile> files, String batchId);

    // -------------------------------------------------------------------------
    // Suivi
    // -------------------------------------------------------------------------

    StatusResponse           getStatus(String batchId);

    RollbackResponse         rollback(String batchId);

    // -------------------------------------------------------------------------
    // Monitoring
    // -------------------------------------------------------------------------

    ActiveIngestionsResponse getActiveIngestions();

    StatsResponse            getStats();

    DetailedHealthResponse   getDetailedHealth();

    StrategiesResponse       getStrategies();
}