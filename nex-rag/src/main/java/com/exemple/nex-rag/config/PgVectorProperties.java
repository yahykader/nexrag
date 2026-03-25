package com.exemple.nexrag.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Propriétés de configuration PgVector.
 *
 * Principe SRP : une seule responsabilité → portage des propriétés PgVector.
 * Principe DIP : les consommateurs dépendent de cette abstraction, pas de @Value éparpillés.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "pgvector")
public class PgVectorProperties {

    @NotBlank
    private String host = "localhost";

    @Min(1) @Max(65535)
    private int port = 5432;

    @NotBlank
    private String database = "vectordb";

    @NotBlank
    private String user = "admin";

    @NotBlank
    private String password;

    @Positive
    private int dimension = 1536;

    @Positive
    private int connectionPoolSize = 10;

    @Positive
    private int connectionTimeoutSeconds = 30;

    /** Construit l'URL JDBC à partir des propriétés. */
    public String buildJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
    }
}