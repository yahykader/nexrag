package com.exemple.nexrag.dto;

import lombok.Value;

/**
 * Résultat d'un scan antivirus.
 *
 * Principe SRP  : unique responsabilité → porter les données d'un résultat de scan.
 * Clean code    : objet valeur immutable, fabriques statiques explicites.
 */
@Value
public class ScanResult {

    String     filename;
    ScanStatus status;
    String     virusName;
    String     errorMessage;

    // -------------------------------------------------------------------------
    // Fabriques statiques
    // -------------------------------------------------------------------------

    public static ScanResult clean(String filename) {
        return new ScanResult(filename, ScanStatus.CLEAN, null, null);
    }

    public static ScanResult infected(String filename, String virusName) {
        return new ScanResult(filename, ScanStatus.INFECTED, virusName, null);
    }

    public static ScanResult error(String filename, String errorMessage) {
        return new ScanResult(filename, ScanStatus.ERROR, null, errorMessage);
    }

    // -------------------------------------------------------------------------
    // Prédicats
    // -------------------------------------------------------------------------

    public boolean isClean()    { return status == ScanStatus.CLEAN;    }
    public boolean isInfected() { return status == ScanStatus.INFECTED; }
    public boolean hasError()   { return status == ScanStatus.ERROR;    }
}