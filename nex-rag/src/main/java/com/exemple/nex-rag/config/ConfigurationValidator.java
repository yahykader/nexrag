package com.exemple.nexrag.config.validation;

import com.exemple.nexrag.config.properties.OpenAiProperties;
import com.exemple.nexrag.config.properties.PgVectorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Validateur de configuration au démarrage.
 *
 * Principe SRP : unique responsabilité → valider et logger la configuration.
 * Principe DIP : dépend des abstractions (Properties), pas des détails.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigurationValidator {

    private final PgVectorProperties pgVectorProperties;
    private final OpenAiProperties openAiProperties;

    @PostConstruct
    public void validate() {
        log.info("🔧 Validation de la configuration...");
        validateOpenAi();
        validatePgVectorConnection();
        log.info("✅ Configuration validée avec succès");
    }

    // -------------------------------------------------------------------------
    // Validation OpenAI
    // -------------------------------------------------------------------------

    private void validateOpenAi() {
        String apiKey = openAiProperties.getApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "❌ 'openai.api.key' est requis dans application.properties"
            );
        }

        if (!apiKey.startsWith("sk-")) {
            log.warn("⚠️ La clé API OpenAI ne commence pas par 'sk-'");
        }

        log.info("✅ OpenAI configuré : key={}, embeddingModel={}, chatModel={}",
            mask(apiKey),
            openAiProperties.getEmbeddingModel(),
            openAiProperties.getChatModel()
        );
    }

    // -------------------------------------------------------------------------
    // Validation PgVector
    // -------------------------------------------------------------------------

    private void validatePgVectorConnection() {
        String jdbcUrl = pgVectorProperties.buildJdbcUrl();
        log.info("🔌 Test connexion PgVector : {}", jdbcUrl);

        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                pgVectorProperties.getUser(),
                pgVectorProperties.getPassword())) {

            if (!conn.isValid(5)) {
                throw new IllegalStateException("Connexion PgVector établie mais invalide.");
            }

            log.info("✅ PgVector connecté : host={}:{}, database={}",
                pgVectorProperties.getHost(),
                pgVectorProperties.getPort(),
                pgVectorProperties.getDatabase()
            );

        } catch (SQLException e) {
            throw new IllegalStateException(
                "❌ Connexion PgVector impossible. " +
                "Assurez-vous que l'extension est activée : " +
                "CREATE EXTENSION IF NOT EXISTS vector;",
                e
            );
        }
    }

    // -------------------------------------------------------------------------
    // Utilitaire
    // -------------------------------------------------------------------------

    private String mask(String key) {
        if (key.length() < 8) return "***";
        return key.substring(0, 7) + "..." + key.substring(key.length() - 4);
    }
}