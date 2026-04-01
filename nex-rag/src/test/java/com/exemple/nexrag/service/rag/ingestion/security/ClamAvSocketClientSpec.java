package com.exemple.nexrag.service.rag.ingestion.security;

import com.exemple.nexrag.config.ClamAvProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires pour ClamAvSocketClient.
 *
 * Approche : ServerSocket local sur port aléatoire simulant le daemon ClamAV.
 * Chaque test démarre un serveur minimal dans un thread séparé, le client se
 * connecte, et la réponse est validée.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : ClamAvSocketClient — Protocole INSTREAM ClamAV via socket TCP")
class ClamAvSocketClientSpec {

    private ServerSocket   serverSocket;
    private ExecutorService executor;
    private ClamAvProperties props;
    private ClamAvSocketClient client;

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0); // port système libre
        executor     = Executors.newSingleThreadExecutor();

        props = new ClamAvProperties();
        props.setHost("localhost");
        props.setPort(serverSocket.getLocalPort());
        props.setTimeout(3000);
        props.setChunkSize(8192);
        props.setMaxFileSize(100L * 1024 * 1024);

        client = new ClamAvSocketClient(props);
    }

    @AfterEach
    void tearDown() throws IOException {
        executor.shutdownNow();
        if (!serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    // -------------------------------------------------------------------------
    // sendInstream
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT recevoir 'stream: OK' après envoi INSTREAM d'un fichier sain")
    void devraitRecevoirOkPourFichierSain() throws Exception {
        // Serveur ClamAV simulé — lit le protocole INSTREAM et répond OK
        executor.submit(() -> simulateClamAvServer(serverSocket, "stream: OK\0"));

        String response = client.sendInstream(
            new ByteArrayInputStream("contenu test".getBytes(StandardCharsets.UTF_8)),
            12L
        );
        assertThat(response).isEqualTo("stream: OK");
    }

    @Test
    @DisplayName("DOIT recevoir 'stream: Eicar FOUND' pour un fichier infecté simulé")
    void devraitRecevoirFoundPourFichierInfecte() throws Exception {
        executor.submit(() ->
            simulateClamAvServer(serverSocket, "stream: Eicar-Test-Signature FOUND\0"));

        String response = client.sendInstream(
            new ByteArrayInputStream("X5O!P%@AP[4\\PZX54".getBytes(StandardCharsets.UTF_8)),
            18L
        );
        assertThat(response).contains("FOUND");
    }

    @Test
    @DisplayName("DOIT lever IOException quand le serveur ClamAV est inaccessible")
    void devraitLeverIOExceptionQuandServeurInaccessible() throws IOException {
        serverSocket.close(); // Fermer avant que le client tente de se connecter
        assertThatThrownBy(() ->
            client.sendInstream(
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                3L
            )
        ).isInstanceOf(IOException.class);
    }

    // -------------------------------------------------------------------------
    // ping
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner true pour ping() quand ClamAV répond PONG")
    void devraitRetournerTruePourPingAvecPong() throws Exception {
        executor.submit(() -> simulatePingServer(serverSocket));
        assertThat(client.ping()).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false pour ping() quand le serveur est inaccessible")
    void devraitRetournerFalsePourPingQuandServeurInaccessible() throws IOException {
        serverSocket.close();
        assertThat(client.ping()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Helpers : serveurs simulés
    // -------------------------------------------------------------------------

    /**
     * Simule le daemon ClamAV pour le protocole INSTREAM.
     * Lit : commande zINSTREAM\0, puis chunks [4-byte size][bytes], puis terminateur 0x0000_0000.
     * Envoie ensuite la réponse fournie.
     */
    private static void simulateClamAvServer(ServerSocket server, String response) {
        try (Socket conn    = server.accept();
             DataInputStream in  = new DataInputStream(conn.getInputStream());
             OutputStream   out = conn.getOutputStream()) {

            // 1. Lire la commande zINSTREAM\0 (10 octets)
            byte[] cmd = new byte[10];
            in.readFully(cmd);

            // 2. Lire les chunks [4-byte int taille][taille octets] jusqu'au terminateur 0
            while (true) {
                int chunkSize = in.readInt();
                if (chunkSize == 0) break;
                byte[] chunk = new byte[chunkSize];
                in.readFully(chunk);
            }

            // 3. Envoyer la réponse
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();

        } catch (Exception e) {
            // Ignorer les erreurs dans le serveur de test
        }
    }

    /**
     * Simule ClamAV pour un PING → répond PONG.
     */
    private static void simulatePingServer(ServerSocket server) {
        try (Socket conn        = server.accept();
            DataInputStream in  = new DataInputStream(conn.getInputStream());
            OutputStream out    = conn.getOutputStream()) {

            // Lire la commande "PING\n" (5 octets) avant de répondre
            byte[] cmd = new byte[5];
            in.readFully(cmd);

            out.write("PONG\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (Exception e) {
            // Ignorer
        }
    }
}
