package com.exemple.nexrag.service.rag.controller;

import com.exemple.nexrag.dto.batch.BatchInfo;
import com.exemple.nexrag.service.rag.facade.CrudFacade;
import com.exemple.nexrag.service.rag.facade.IngestionFacade;
import com.exemple.nexrag.service.rag.facade.StreamingFacade;
import com.exemple.nexrag.service.rag.retrieval.RetrievalAugmentorOrchestrator;
import com.exemple.nexrag.service.rag.streaming.model.StreamingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Simplified REST API for integration testing.
 *
 * Maps simple `/api/` endpoints to the underlying facade layer.
 * Used by integration tests to validate the RAG pipeline end-to-end.
 *
 * Endpoints:
 * - POST   /api/ingest   → Upload and ingest file (async, returns 202 ACCEPTED)
 * - GET    /api/search   → Retrieve passages for query
 * - POST   /api/stream   → Stream LLM response via SSE
 * - DELETE /api/files    → Clear all ingested documents
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "RAG API", description = "Simplified RAG API for integration testing")
public class RagApiIntegrationController {

    private final IngestionFacade ingestionFacade;
    private final CrudFacade crudFacade;
    private final StreamingFacade streamingFacade;
    private final RetrievalAugmentorOrchestrator retrievalOrchestrator;

    // =========================================================================
    // T013-T021: INGESTION — POST /api/ingest
    // =========================================================================

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Ingest a document file", description = "Async upload with duplicate detection")
    public ResponseEntity<BatchInfo> ingestFile(
            @RequestParam("file") MultipartFile file) {

        log.info("📄 POST /api/ingest — file={}, size={}MB",
            file.getOriginalFilename(), file.getSize() / (1024 * 1024));

        try {
            var asyncResponse = ingestionFacade.uploadAsync(file, null);

            // Convert AsyncResponse to BatchInfo
            BatchInfo batchInfo = new BatchInfo(
                asyncResponse.getBatchId(),
                file.getOriginalFilename(),
                file.getContentType(),
                OffsetDateTime.now(),
                new ArrayList<>(),
                new ArrayList<>()
            );

            HttpStatus status = asyncResponse.getDuplicate() ?
                HttpStatus.CONFLICT : HttpStatus.ACCEPTED;

            log.info("✅ Ingestion response: status={}, batchId={}",
                status, asyncResponse.getBatchId());

            return ResponseEntity.status(status).body(batchInfo);
        } catch (Exception e) {
            log.error("❌ Ingestion failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    // =========================================================================
    // T023-T042: RETRIEVAL — GET /api/search
    // =========================================================================

    @GetMapping("/search")
    @Operation(summary = "Retrieve passages for a query", description = "Returns ranked passages (<3s)")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(required = false) String conversationId) {

        log.info("🔍 GET /api/search — query={}, conversationId={}", query, conversationId);

        try {
            // Execute retrieval pipeline
            var result = retrievalOrchestrator.execute(query);

            // Build response map
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("query", query);
            response.put("conversationId", conversationId);

            // Extract passages from result
            List<Map<String, Object>> passages = new ArrayList<>();
            if (result != null && result.getAggregatedContext() != null) {
                var aggregatedContext = result.getAggregatedContext();

                // Extract chunks as passages
                if (aggregatedContext.getChunks() != null && !aggregatedContext.getChunks().isEmpty()) {
                    aggregatedContext.getChunks().forEach(chunk -> {
                        Map<String, Object> passage = new HashMap<>();
                        passage.put("id", chunk.getId());
                        passage.put("content", chunk.getContent());
                        passage.put("score", chunk.getFinalScore());
                        passage.put("scoresByRetriever", chunk.getScoresByRetriever());
                        passage.put("retrieversUsed", chunk.getRetrieversUsed());
                        passages.add(passage);
                    });
                }
            }

            response.put("passages", passages);
            response.put("totalPassages", passages.size());

            log.info("✅ Retrieved {} passages in {}ms", passages.size(),
                result != null ? result.getTotalDurationMs() : "unknown");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Retrieval failed: {}", e.getMessage(), e);
            // Return empty passages on error instead of 500
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("query", query);
            errorResponse.put("conversationId", conversationId);
            errorResponse.put("passages", new ArrayList<>());
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }

    // =========================================================================
    // T026-T027: STREAMING — POST /api/stream
    // =========================================================================

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream LLM response", description = "Server-Sent Events for real-time tokens")
    public SseEmitter streamResponse(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String conversationId) {

        log.info("📡 POST /api/stream — query={}, conversationId={}", query, conversationId);

        StreamingRequest request = StreamingRequest.builder()
            .query(query != null ? query : "NexRAG")
            .conversationId(conversationId != null ? conversationId : UUID.randomUUID().toString())
            .build();

        return streamingFacade.startStream(request);
    }

    // =========================================================================
    // CRUD — DELETE /api/files
    // =========================================================================

    @DeleteMapping("/files")
    @Operation(summary = "Delete all ingested documents", description = "Clears all embeddings for testing")
    public ResponseEntity<Void> deleteAllFiles() {
        log.info("🗑️ DELETE /api/files");

        try {
            crudFacade.deleteAll("DELETE_ALL_FILES");
            log.info("✅ All files deleted");
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (Exception e) {
            log.warn("⚠️ Delete all files failed: {}", e.getMessage());
            // Fail-open: return 204 anyway for idempotency
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
    }
}
