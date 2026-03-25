package com.exemple.nexrag.service.rag.ingestion.deduplication.file;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Calcul de hash SHA-256 pour les fichiers et données binaires.
 *
 * Principe SRP : unique responsabilité → calculer des empreintes cryptographiques.
 * Clean code   : élimine les trois méthodes alias
 *                ({@code computeHash(byte[])}, {@code calculateHash(byte[])},
 *                 {@code computeHash(MultipartFile)}) fusionnées en deux méthodes claires.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Component
public class HashComputer {

    private static final String ALGORITHM = "SHA-256";

    /**
     * Calcule le hash SHA-256 d'un tableau de bytes.
     *
     * @param bytes données à hacher
     * @return hash encodé en Base64
     */
    public String compute(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return Base64.getEncoder().encodeToString(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti par la JVM — ne peut pas arriver en pratique
            throw new IllegalStateException(ALGORITHM + " non disponible", e);
        }
    }

    /**
     * Calcule le hash SHA-256 du contenu d'un fichier uploadé.
     *
     * @param file fichier à hacher
     * @return hash encodé en Base64
     * @throws IOException si la lecture du fichier échoue
     */
    public String compute(MultipartFile file) throws IOException {
        return compute(file.getBytes());
    }

    /**
     * Retourne les 16 premiers caractères d'un hash pour les logs.
     * Évite d'exposer le hash complet dans les journaux.
     */
    public String toShortHash(String hash) {
        if (hash == null || hash.length() < 16) return hash;
        return hash.substring(0, 16) + "...";
    }
}