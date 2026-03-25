package com.exemple.nexrag.service.rag.ingestion.analyzer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Statistiques de stockage des images extraites.
 *
 * Principe SRP : unique responsabilité → mesurer l'occupation du répertoire.
 * Clean code   : extrait {@code getStorageStats()} et le record {@code StorageStats}
 *                hors de {@link ImageSaver} — qui n'avait pas à connaître
 *                la taille totale du disque.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageStatsService {

    private final ImageStorageProperties props;

    /**
     * Retourne les statistiques du répertoire de stockage.
     */
    public StorageStats getStats() throws IOException {
        Path path = Paths.get(props.getStoragePath());

        if (!Files.exists(path)) {
            return new StorageStats(0, 0, props.getStoragePath());
        }

        long fileCount = Files.list(path)
            .filter(Files::isRegularFile)
            .count();

        long totalSize = Files.walk(path)
            .filter(Files::isRegularFile)
            .mapToLong(p -> {
                try { return Files.size(p); }
                catch (IOException e) { return 0; }
            })
            .sum();

        return new StorageStats(fileCount, totalSize, props.getStoragePath());
    }

    // -------------------------------------------------------------------------
    // Record
    // -------------------------------------------------------------------------

    public record StorageStats(
        long   fileCount,
        long   totalSizeBytes,
        String path
    ) {
        public double totalSizeMb() { return totalSizeBytes / (1024.0 * 1024.0); }

        public String formattedSize() {
            double mb = totalSizeMb();
            return mb < 1024
                ? String.format("%.2f MB", mb)
                : String.format("%.2f GB", mb / 1024.0);
        }
    }
}