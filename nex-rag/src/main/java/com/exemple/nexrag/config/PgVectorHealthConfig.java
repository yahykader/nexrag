package com.exemple.nexrag.config;

import com.exemple.nexrag.config.properties.OpenAiProperties;
import com.exemple.nexrag.config.properties.PgVectorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Configuration du Health Indicator Actuator pour PgVector.
 *
 * Principe SRP : unique responsabilité → exposer l'état de santé PgVector.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openai.enabled", havingValue = "true")
public class PgVectorHealthConfig {

    private final PgVectorProperties pgVectorProps;
    private final OpenAiProperties   openAiProps;

    @Bean
    public HealthIndicator pgVectorHealthIndicator() {
        return this::buildHealth;
    }

    // -------------------------------------------------------------------------
    // Logique de santé
    // -------------------------------------------------------------------------

    private Health buildHealth() {
        try (Connection conn = DriverManager.getConnection(
                pgVectorProps.buildJdbcUrl(),
                pgVectorProps.getUser(),
                pgVectorProps.getPassword())) {

            if (!conn.isValid(5)) {
                return Health.down()
                    .withDetail("error", "Connection validation failed")
                    .build();
            }

            return Health.up()
                .withDetail("pgvector.host",      pgVectorProps.getHost() + ":" + pgVectorProps.getPort())
                .withDetail("pgvector.database",   pgVectorProps.getDatabase())
                .withDetail("pgvector.status",     "connected")
                .withDetail("openai.configured",   openAiProps.getApiKey() != null)
                .withDetail("embedding.dimension", pgVectorProps.getDimension())
                .build();

        } catch (Exception e) {
            return Health.down()
                .withDetail("error",         e.getMessage())
                .withDetail("pgvector.host", pgVectorProps.getHost() + ":" + pgVectorProps.getPort())
                .build();
        }
    }
}