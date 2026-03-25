package com.exemple.nexrag.dto;

/**
 * Statut possible d'un scan antivirus.
 *
 * Principe SRP : unique responsabilité → représenter l'état d'un scan.
 * Principe OCP : ajouter un statut ne modifie pas les classes existantes.
 */
public enum ScanStatus {
    /** Fichier analysé, aucune menace détectée. */
    CLEAN,
    /** Virus ou malware détecté. */
    INFECTED,
    /** Erreur technique lors du scan. */
    ERROR
}