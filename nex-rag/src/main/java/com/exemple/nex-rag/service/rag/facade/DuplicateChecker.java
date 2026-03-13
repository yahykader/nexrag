package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.dto.DuplicateSummary;
import com.exemple.nexrag.service.rag.ingestion.deduplication.DeduplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pré-vérifie les doublons sur une liste de fichiers.
 *
 * Principe SRP : unique responsabilité → détecter les doublons avant ingestion.
 * Clean code   : élimine la logique dupliquée entre {@code uploadBatch}
 *                et {@code uploadBatchDetailed}.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DuplicateChecker {

    private final DeduplicationService deduplicationService;

    /**
     * Analyse une liste de fichiers et retourne un résumé des doublons.
     * En cas d'erreur sur un fichier individuel, le traitement continue
     * (comportement non-bloquant).
     *
     * @param files liste de fichiers à analyser
     * @return résumé des doublons détectés
     */
    public DuplicateSummary check(List<MultipartFile> files) {
        List<String>         duplicateFilenames  = new ArrayList<>();
        Map<String, String>  existingBatchIds    = new HashMap<>();

        for (MultipartFile file : files) {
            checkSingle(file, duplicateFilenames, existingBatchIds);
        }

        if (!duplicateFilenames.isEmpty()) {
            log.warn("⚠️ {} doublon(s) détecté(s) sur {} fichiers",
                duplicateFilenames.size(), files.size());
        }

        return DuplicateSummary.builder()
            .count(duplicateFilenames.size())
            .filenames(duplicateFilenames)
            .existingBatchIds(existingBatchIds)
            .build();
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private void checkSingle(
            MultipartFile file,
            List<String> duplicateFilenames,
            Map<String, String> existingBatchIds) {
        try {
            byte[] content        = file.getBytes();
            String hash           = deduplicationService.computeHash(content);

            if (!deduplicationService.isDuplicateByHash(hash)) {
                return;
            }

            String existingBatchId = deduplicationService.getExistingBatchId(hash);
            duplicateFilenames.add(file.getOriginalFilename());
            existingBatchIds.put(file.getOriginalFilename(), existingBatchId);

            log.warn("⚠️ Doublon : {} → batch existant : {}",
                file.getOriginalFilename(), existingBatchId);

        } catch (Exception e) {
            log.warn("⚠️ Erreur vérification doublon pour {} (traitement continue) : {}",
                file.getOriginalFilename(), e.getMessage());
        }
    }
}