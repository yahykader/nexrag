package com.exemple.nexrag.service.rag.ingestion.security;

import com.exemple.nexrag.config.ClamAvProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Client de communication bas-niveau avec le daemon ClamAV via TCP.
 *
 * Principe SRP : unique responsabilité → gérer le protocole INSTREAM de ClamAV.
 * Clean code   : élimine la duplication entre {@code scanWithClamAV}
 *                et {@code scanBytesWithClamAV} — un seul algorithme
 *                paramétré par un {@link InputStream}.
 *
 * @author ayhyaoui
 * @version 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class ClamAvSocketClient {

    private static final byte[] INSTREAM_COMMAND  = "zINSTREAM\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PING_COMMAND      = "PING\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VERSION_COMMAND   = "VERSION\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CHUNK_END         = new byte[]{0, 0, 0, 0};
    private static final String PONG_RESPONSE     = "PONG";

    private final ClamAvProperties props;

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    /**
     * Envoie les données d'un {@link InputStream} à ClamAV et retourne la réponse brute.
     *
     * <p>Protocole INSTREAM : commande → chunks (4 bytes taille + données) → chunk vide.
     *
     * @param inputStream flux des données à scanner
     * @param totalSize   taille totale pour le log
     * @return réponse brute de ClamAV
     * @throws IOException en cas d'erreur réseau
     */
    public String sendInstream(InputStream inputStream, long totalSize) throws IOException {
        try (Socket socket = openSocket();
             OutputStream out = socket.getOutputStream();
             InputStream  in  = socket.getInputStream()) {

            sendInstreamCommand(out, inputStream);
            log.debug("📤 Données envoyées à ClamAV ({} bytes)", totalSize);
            return readResponse(in);
        }
    }

    /**
     * Vérifie que ClamAV répond au PING.
     *
     * @return {@code true} si ClamAV répond PONG
     */
    public boolean ping() {
        try (Socket socket = openSocket();
             OutputStream out = socket.getOutputStream();
             BufferedReader reader = asciiReader(socket)) {

            out.write(PING_COMMAND);
            out.flush();
            String line = reader.readLine();
            return line != null && PONG_RESPONSE.equalsIgnoreCase(line.trim());

        } catch (IOException e) {
            log.warn("✗ ClamAV non disponible : {}", e.getMessage());
            return false;
        }
    }

    /**
     * Récupère la version du daemon ClamAV.
     *
     * @return chaîne de version ou {@code null} si indisponible
     */
    public String version() {
        try (Socket socket = openSocket();
             OutputStream out = socket.getOutputStream();
             BufferedReader reader = asciiReader(socket)) {

            out.write(VERSION_COMMAND);
            out.flush();
            String line = reader.readLine();
            return line != null ? line.trim() : null;

        } catch (IOException e) {
            log.warn("Impossible de récupérer la version ClamAV : {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private Socket openSocket() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(props.getHost(), props.getPort()), props.getTimeout());
        socket.setSoTimeout(props.getTimeout());
        return socket;
    }

    private void sendInstreamCommand(OutputStream out, InputStream inputStream) throws IOException {
        out.write(INSTREAM_COMMAND);
        out.flush();

        byte[] buffer = new byte[props.getChunkSize()];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            out.write(ByteBuffer.allocate(4).putInt(bytesRead).array());
            out.write(buffer, 0, bytesRead);
        }

        out.write(CHUNK_END);
        out.flush();
    }

    private String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int bytesRead;

        while ((bytesRead = in.read(buffer)) != -1) {
            response.write(buffer, 0, bytesRead);
            if (buffer[bytesRead - 1] == 0) break; // ClamAV termine par '\0'
        }

        return response.toString(StandardCharsets.UTF_8).trim();
    }

    private BufferedReader asciiReader(Socket socket) throws IOException {
        return new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)
        );
    }
}