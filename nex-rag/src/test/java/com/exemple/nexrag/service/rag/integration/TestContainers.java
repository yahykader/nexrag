package com.exemple.nexrag.service.rag.integration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;

import java.time.Duration;

/**
 * Singleton Container Pattern — Testcontainers.
 *
 * Les trois containers (PostgreSQL, Redis, ClamAV) sont démarrés UNE SEULE FOIS
 * pour toute la JVM de test via le bloc {@code static {}}.
 *
 * Toutes les classes de test qui héritent d'AbstractIntegrationSpec bénéficient
 * automatiquement de ces containers sans les redémarrer entre les classes.
 *
 * Ryuk (resource reaper de Testcontainers) arrête les containers proprement
 * à la fin du processus Maven.
 */
public abstract class TestContainers {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("nexrag_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withReuse(true)
            .withStartupTimeout(Duration.ofMinutes(2));

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true)
            .withStartupTimeout(Duration.ofSeconds(30));

    @SuppressWarnings("resource")
    static final GenericContainer<?> CLAMAV =
        new GenericContainer<>("clamav/clamav:latest")
            .withExposedPorts(3310)
            .waitingFor(Wait.forListeningPort())
            .withReuse(true)
            .withStartupTimeout(Duration.ofMinutes(3));

    // -------------------------------------------------------------------------
    // Démarrage unique — exécuté une seule fois par la JVM de test
    // -------------------------------------------------------------------------

    static {
        Startables.deepStart(POSTGRES, REDIS, CLAMAV).join();
    }
}