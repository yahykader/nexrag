package com.exemple.nexrag.dto.deduplication.file;

/**
 * Résultat d'une vérification de doublon.
 *
 * Principe SRP : unique responsabilité → porter les données d'un contrôle de déduplication.
 * Clean code   : {@code getShortHash()} centralisé ici — élimine la duplication
 *                entre les anciens records {@code DuplicationInfo} et {@code FileInfo}.
 */
public record DuplicationInfo(
    boolean  isDuplicate,
    String   hash,
    String   existingBatchId
) {
    /** Retourne les 16 premiers caractères du hash pour les logs. */
    public String shortHash() {
        return hash != null && hash.length() >= 16
            ? hash.substring(0, 16) + "..."
            : hash;
    }
}