package com.exemple.nexrag.service.rag.ingestion.strategy;

import com.exemple.nexrag.exception.IngestionException;
import com.exemple.nexrag.service.rag.ingestion.strategy.IngestionResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Contrat d'ingestion pour un type de fichier donné.
 *
 * Principe OCP : chaque format = une nouvelle implémentation,
 *                sans modifier l'orchestrateur ni la config.
 * Principe ISP : interface fine — trois méthodes essentielles.
 * Clean code   : {@code throws Exception} remplacé par des types déclarés.
 *                {@code getPriority()} détermine l'ordre de sélection
 *                (valeur basse = priorité haute).
 */
public interface IngestionStrategy {

    /**
     * Indique si cette stratégie peut traiter le fichier donné.
     *
     * @param file      fichier à tester
     * @param extension extension en minuscules (ex : "pdf", "docx")
     * @return {@code true} si la stratégie prend en charge ce format
     */
    boolean canHandle(MultipartFile file, String extension);

    /**
     * Ingère le fichier et retourne les statistiques d'embeddings créés.
     *
     * @param file    fichier à ingérer
     * @param batchId identifiant du batch courant
     * @return résultat de l'ingestion
     * @throws IOException        si la lecture du fichier échoue
     * @throws IngestionException si le traitement métier échoue
     */
    IngestionResult ingest(MultipartFile file, String batchId)
        throws IOException, IngestionException;

    /**
     * Nom lisible de la stratégie (utilisé pour les logs et métriques).
     */
    String getName();

    /**
     * Priorité de sélection — valeur basse = priorité haute.
     * Défaut : 5 (stratégie générique de fallback).
     */
    default int getPriority() {
        return 5;
    }
}