package com.exemple.nexrag.service.rag.ingestion.tracker;

import com.exemple.nexrag.dto.batch.BatchInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre des métadonnées de batch — utilisé pour les opérations CRUD.
 *
 * Principe SRP : unique responsabilité → maintenir les informations descriptives
 *                des batches (nom fichier, timestamps, listes d'IDs pour CRUD).
 * Clean code   : extrait la deuxième map ({@code batches}) de
 *                {@link IngestionTracker} pour isoler la logique CRUD.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Component
public class BatchInfoRegistry {

    private final Map<String, BatchInfo> registry = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Enregistrement
    // -------------------------------------------------------------------------

    public void register(String batchId, String filename, String mimeType) {
        BatchInfo info = new BatchInfo(
            batchId,
            filename,
            mimeType,
            LocalDateTime.now(),
            new ArrayList<>(),
            new ArrayList<>()
        );
        registry.put(batchId, info);
        log.info("📊 [BatchInfo] Batch enregistré : {} — {}", batchId, filename);
    }

    // -------------------------------------------------------------------------
    // Mise à jour des IDs
    // -------------------------------------------------------------------------

    public void addTextEmbeddingId(String batchId, String embeddingId) {
        Optional.ofNullable(registry.get(batchId))
            .ifPresent(info -> info.textEmbeddings().add(embeddingId));
    }

    public void addImageEmbeddingId(String batchId, String embeddingId) {
        Optional.ofNullable(registry.get(batchId))
            .ifPresent(info -> info.imageEmbeddings().add(embeddingId));
    }

    // -------------------------------------------------------------------------
    // Lecture
    // -------------------------------------------------------------------------

    public Optional<BatchInfo> get(String batchId) {
        return Optional.ofNullable(registry.get(batchId));
    }

    public Map<String, BatchInfo> getAll() {
        return new HashMap<>(registry);
    }

    public boolean contains(String batchId) {
        return registry.containsKey(batchId);
    }

    public int size() {
        return registry.size();
    }

    public int totalEmbeddings() {
        return registry.values().stream()
            .mapToInt(BatchInfo::totalEmbeddings)
            .sum();
    }

    // -------------------------------------------------------------------------
    // Nettoyage
    // -------------------------------------------------------------------------

    public void remove(String batchId) {
        registry.remove(batchId);
    }

    public void clear() {
        registry.clear();
    }
}