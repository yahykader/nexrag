package com.exemple.nexrag.service.rag.ingestion.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * DAO pour les requêtes SQL sur les tables d'embeddings.
 *
 * Principe SRP : unique responsabilité → exécuter les requêtes SQL (lecture seule).
 * Clean code   : centralise toutes les requêtes SQL, élimine {@code String.format}
 *                éparpillés dans le repository.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingQueryDao {

    private static final String SQL_FIND_BY_BATCH =
        "SELECT embedding_id FROM %s WHERE metadata->>'batchId' = ?";

    private static final String SQL_COUNT_BY_BATCH =
        "SELECT COUNT(*) FROM %s WHERE metadata->>'batchId' = ?";

    private static final String SQL_FIND_ALL =
        "SELECT embedding_id FROM %s";

    private static final String SQL_COUNT_ALL =
        "SELECT COUNT(*) FROM %s";

    private final JdbcTemplate jdbcTemplate;

    // -------------------------------------------------------------------------
    // Recherche par batchId
    // -------------------------------------------------------------------------

    public List<String> findIdsByBatchId(String tableName, String batchId) {
        try {
            List<String> ids = jdbcTemplate.queryForList(
                sql(SQL_FIND_BY_BATCH, tableName), String.class, batchId
            );
            log.debug("📊 {} IDs trouvés dans '{}' pour batch={}", ids.size(), tableName, batchId);
            return ids;
        } catch (Exception e) {
            log.error("❌ Erreur findIdsByBatchId — table={}, batchId={}", tableName, batchId, e);
            return Collections.emptyList();
        }
    }

    public int countByBatchId(String tableName, String batchId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                sql(SQL_COUNT_BY_BATCH, tableName), Integer.class, batchId
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("❌ Erreur countByBatchId — table={}, batchId={}", tableName, batchId, e);
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Lecture globale
    // -------------------------------------------------------------------------

    public List<String> findAllIds(String tableName) {
        try {
            return jdbcTemplate.queryForList(sql(SQL_FIND_ALL, tableName), String.class);
        } catch (Exception e) {
            log.error("❌ Erreur findAllIds — table={}", tableName, e);
            return Collections.emptyList();
        }
    }

    public int countAll(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(sql(SQL_COUNT_ALL, tableName), Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("❌ Erreur countAll — table={}", tableName, e);
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Utilitaire
    // -------------------------------------------------------------------------

    private String sql(String template, String tableName) {
        return String.format(template, tableName);
    }
}