package com.exemple.nexrag.service.rag.ingestion.deduplication.text;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Normalisation et hachage de textes pour la déduplication.
 *
 * Principe SRP : unique responsabilité → normaliser et hacher du texte.
 * Clean code   : extrait {@code hash()} de {@link TextDeduplicationService}
 *                et évite la duplication avec {@link HashComputer}
 *                (qui hache des bytes bruts, ici on normalise du texte d'abord).
 *
 * @author RAG Team
 * @version 1.0
 */
@Slf4j
@Component
public class TextNormalizer {

    private static final String ALGORITHM = "SHA-256";

    /**
     * Normalise un texte et retourne son hash SHA-256 hexadécimal.
     *
     * <p>Normalisation : trim + lowercase + espaces multiples → espace simple.
     *
     * @param text texte à hacher
     * @return hash SHA-256 en hexadécimal
     */
    public String hash(String text) {
        try {
            String normalized = normalize(text);
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            byte[] bytes = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti par la JVM
            throw new IllegalStateException(ALGORITHM + " non disponible", e);
        }
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private String normalize(String text) {
        return text.trim()
            .toLowerCase()
            .replaceAll("\\s+", " ");
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}