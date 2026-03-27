package com.exemple.nexrag.service.rag.ingestion.util;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * Détecte le type MIME réel d'un fichier en inspectant ses magic bytes via Apache Tika.
 *
 * Principe SRP : unique responsabilité → détecter le type MIME à partir des bytes bruts.
 * Clean code   : délègue entièrement à Tika pour éviter les règles ad hoc par extension.
 */
@Component
public class FileTypeDetector {

    private static final Tika TIKA = new Tika();
    private static final String FALLBACK_MIME = "application/octet-stream";

    /**
     * Détecte le type MIME réel en inspectant les magic bytes.
     *
     * @param content octets bruts du fichier (non null)
     * @return type MIME détecté ; jamais null (fallback = "application/octet-stream")
     */
    public String detect(byte[] content) {
        if (content == null || content.length == 0) {
            return FALLBACK_MIME;
        }
        String detected = TIKA.detect(content);
        return detected != null ? detected : FALLBACK_MIME;
    }
}
