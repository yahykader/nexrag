package com.exemple.nexrag.service.rag.ingestion.security;

import com.exemple.nexrag.config.ClamAvProperties;
import com.exemple.nexrag.exception.AntivirusScanException;
import com.exemple.nexrag.service.rag.ingestion.security.ClamAvResponseParser;
import com.exemple.nexrag.service.rag.ingestion.security.ClamAvSocketClient;
import com.exemple.nexrag.dto.ScanResult;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Path;

/**
 * Service de scan antivirus via le daemon ClamAV (protocole INSTREAM).
 *
 * Principe SRP  : unique responsabilité → orchestrer le scan antivirus.
 *                 La communication socket est dans {@link ClamAvSocketClient}.
 *                 Le parsing de réponse est dans {@link ClamAvResponseParser}.
 * Principe DIP  : dépend des abstractions client et parser.
 * Clean code    : élimine la duplication entre scan fichier et scan bytes —
 *                 un seul chemin d'exécution via {@link InputStream}.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
public class AntivirusScanner {

    private final ClamAvProperties    props;
    private final ClamAvSocketClient  socketClient;
    private final ClamAvResponseParser responseParser;

    public AntivirusScanner(
            ClamAvProperties     props,
            ClamAvSocketClient   socketClient,
            ClamAvResponseParser responseParser) {

        this.props          = props;
        this.socketClient   = socketClient;
        this.responseParser = responseParser;

        log.info("📡 AntivirusScanner initialisé — enabled={}, host={}:{}, timeout={}ms",
            props.isEnabled(), props.getHost(), props.getPort(), props.getTimeout());
    }

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    /**
     * Scanne un fichier depuis son chemin.
     */
    public ScanResult scanFile(Path path) throws AntivirusScanException {
        return scanFile(path.toFile());
    }

    /**
     * Scanne un fichier {@link File}.
     */
    public ScanResult scanFile(File file) throws AntivirusScanException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("Fichier invalide ou inexistant");
        }
        validateSize(file.length(), file.getName());
        log.info("🔍 Scan fichier : {} ({} bytes)", file.getName(), file.length());

        try (InputStream is = new FileInputStream(file)) {
            return execute(is, file.getName(), file.length());
        } catch (IOException e) {
            throw new AntivirusScanException("Erreur scan antivirus : " + e.getMessage(), e);
        }
    }

    /**
     * Scanne des données en mémoire.
     */
    public ScanResult scanBytes(byte[] data, String filename) throws AntivirusScanException {
        if (data == null) throw new IllegalArgumentException("Données null");
        validateSize(data.length, filename);
        log.info("🔍 Scan bytes : {} ({} bytes)", filename, data.length);

        try (InputStream is = new ByteArrayInputStream(data)) {
            return execute(is, filename, data.length);
        } catch (IOException e) {
            throw new AntivirusScanException("Erreur scan antivirus : " + e.getMessage(), e);
        }
    }

    /**
     * Vérifie la disponibilité du daemon ClamAV.
     */
    public boolean isAvailable() {
        if (!props.isEnabled()) return true;
        boolean available = socketClient.ping();
        log.debug("{} ClamAV disponible : {}", available ? "✓" : "✗", available);
        return available;
    }

    /**
     * Retourne la version du daemon ClamAV.
     */
    public String getVersion() {
        return props.isEnabled() ? socketClient.version() : "disabled";
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    /**
     * Point d'exécution unique — élimine la duplication entre scan fichier/bytes.
     */
    private ScanResult execute(InputStream inputStream, String filename, long size)
            throws IOException, AntivirusScanException {

        if (!props.isEnabled()) {
            log.debug("🔓 Scan désactivé — fichier accepté : {}", filename);
            return ScanResult.clean(filename);
        }

        String rawResponse = socketClient.sendInstream(inputStream, size);
        return responseParser.parse(filename, rawResponse);
    }

    private void validateSize(long size, String filename) throws AntivirusScanException {
        if (size > props.getMaxFileSize()) {
            throw new AntivirusScanException(String.format(
                "Fichier trop volumineux pour le scan : %d bytes (max : %d bytes) — %s",
                size, props.getMaxFileSize(), filename
            ));
        }
    }
}