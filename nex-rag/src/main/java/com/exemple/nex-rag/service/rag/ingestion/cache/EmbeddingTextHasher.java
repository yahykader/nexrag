package com.exemple.nexrag.service.rag.ingestion.cache;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Calcul de hash de texte pour les clés Redis du cache d'embeddings.
 *
 * Principe SRP : unique responsabilité → transformer un texte en identifiant de clé Redis.
 * Clean code   : remplace {@code Integer.toHexString(text.hashCode())} — faible
 *                et sujet aux collisions — par un SHA-256 tronqué, cohérent
 *                avec la stratégie de hash utilisée dans le reste du système.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Component
public class EmbeddingTextHasher {

    private static final String ALGORITHM    = "SHA-256";
    private static final int    HASH_LENGTH  = 16; // 16 premiers chars hex suffisent pour les clés cache

    /**
     * Calcule un hash court du texte pour construire une clé Redis.
     *
     * @param text texte à hacher
     * @return hash hexadécimal tronqué à {@value HASH_LENGTH} caractères
     */
    public String hash(String text) {
        try {
            MessageDigest md    = MessageDigest.getInstance(ALGORITHM);
            byte[]        bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes).substring(0, HASH_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " non disponible", e);
        }
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}