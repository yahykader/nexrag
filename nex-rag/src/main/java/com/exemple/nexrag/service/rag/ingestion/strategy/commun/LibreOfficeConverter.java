package com.exemple.nexrag.service.rag.ingestion.strategy.commun;

import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service de conversion XLSX → PDF via LibreOffice.
 *
 * Principe SRP  : unique responsabilité → convertir un fichier XLSX en PDF.
 *                 Détection OS, exécution processus, métriques.
 * Clean code    : toutes les propriétés sont lues via {@link XlsxProperties}
 *                 (préfixe {@code document.xlsx.libreoffice.*}).
 *                 Les messages d'erreur référencent les nouvelles clés YAML.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LibreOfficeConverter {

    private static final String METRIC_CONVERT = "libreoffice_convert";
    private static final String YAML_PATH_KEY  = "document.xlsx.libreoffice.soffice-path";

    private final XlsxProperties props;
    private final RAGMetrics      ragMetrics;

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    /**
     * Convertit un fichier XLSX en PDF via LibreOffice.
     *
     * @param xlsxBytes contenu du fichier XLSX
     * @param baseName  nom de base sans extension (pour nommer le PDF généré)
     * @return contenu du PDF généré en bytes
     * @throws IOException           si la conversion échoue
     * @throws IllegalStateException si LibreOffice est désactivé dans la config
     */
    public byte[] convert(byte[] xlsxBytes, String baseName) throws IOException {
        assertEnabled();

        String sofficeBinary = resolveSofficeExecutable();
        Path   tempDir       = Files.createTempDirectory("xlsx2pdf_");
        Path   inputXlsx     = tempDir.resolve(baseName + ".xlsx");
        Path   outDir        = tempDir.resolve("out");
        Files.createDirectories(outDir);

        try {
            Files.write(inputXlsx, xlsxBytes,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            byte[] pdfBytes = runConversion(sofficeBinary, inputXlsx, outDir, baseName);

            log.info("✅ [LibreOffice] Conversion réussie : {} octets", pdfBytes.length);
            return pdfBytes;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Conversion LibreOffice interrompue", e);
        } finally {
            cleanup(inputXlsx, outDir, tempDir);
        }
    }

    /**
     * Indique si LibreOffice est activé dans la configuration.
     *
     * @return {@code true} si {@code document.xlsx.libreoffice.enabled=true}
     */
    public boolean isEnabled() {
        return props.getLibreoffice().isEnabled();
    }

    // -------------------------------------------------------------------------
    // Privé — exécution du processus
    // -------------------------------------------------------------------------

    private byte[] runConversion(String binary, Path inputXlsx,
                                  Path outDir, String baseName)
            throws IOException, InterruptedException {

        List<String> cmd = List.of(
            binary, "--headless", "--nologo",
            "--nofirststartwizard", "--norestore",
            "--convert-to", "pdf",
            "--outdir", outDir.toAbsolutePath().toString(),
            inputXlsx.toAbsolutePath().toString()
        );

        long    start   = System.currentTimeMillis();
        Process process = launchProcess(cmd, binary);
        String  output  = readAll(process.getInputStream());
        boolean done    = process.waitFor(timeout(), TimeUnit.SECONDS);

        ragMetrics.recordApiCall(METRIC_CONVERT, System.currentTimeMillis() - start);

        if (!done) {
            process.destroyForcibly();
            ragMetrics.recordApiError(METRIC_CONVERT);
            throw new IOException(String.format(
                "Timeout LibreOffice (%ds). Output=%s", timeout(), output));
        }

        if (process.exitValue() != 0) {
            ragMetrics.recordApiError(METRIC_CONVERT);
            throw new IOException(String.format(
                "Échec LibreOffice (exit=%d). Output=%s", process.exitValue(), output));
        }

        Path pdfPath = resolvePdfPath(outDir, baseName, output);
        log.info("✅ [LibreOffice] PDF généré : {} ({} KB)",
            pdfPath.getFileName(), Files.size(pdfPath) / 1024);

        return Files.readAllBytes(pdfPath);
    }

    // -------------------------------------------------------------------------
    // Privé — résolution de l'exécutable soffice
    // -------------------------------------------------------------------------

    private String resolveSofficeExecutable() {
        String configured = props.getLibreoffice().getSofficePath();

        if (configured != null && !configured.isBlank()) {
            Path p = Paths.get(configured);
            if (Files.exists(p)) {
                log.info("✅ [LibreOffice] Exécutable (configuré) : {}", p.toAbsolutePath());
                return p.toAbsolutePath().toString();
            }
            throw new IllegalStateException(
                "Chemin configuré dans " + YAML_PATH_KEY + " introuvable : " + p);
        }

        String os       = System.getProperty("os.name").toLowerCase();
        String detected = os.contains("win") ? detectWindows()
                        : os.contains("mac") ? detectMac()
                        : detectLinux();

        if (detected != null) {
            log.info("✅ [LibreOffice] Exécutable (auto-détecté) : {}", detected);
            return detected;
        }

        String fallback = os.contains("win") ? "soffice.exe" : "soffice";
        log.warn("⚠️ [LibreOffice] Non trouvé localement — utilisation PATH : {}", fallback);
        log.warn("⚠️ [LibreOffice] Configurez {} pour éviter ce fallback", YAML_PATH_KEY);
        return fallback;
    }

    private String detectWindows() {
        return firstExisting(List.of(
            "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
            "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe"
        ));
    }

    private String detectLinux() {
        return firstExecutable(List.of(
            "/usr/bin/soffice",
            "/usr/local/bin/soffice",
            "/opt/libreoffice/program/soffice",
            "/usr/lib/libreoffice/program/soffice"
        ));
    }

    private String detectMac() {
        return firstExecutable(List.of(
            "/Applications/LibreOffice.app/Contents/MacOS/soffice",
            "/usr/local/bin/soffice"
        ));
    }

    private String firstExisting(List<String> candidates) {
        return candidates.stream()
            .filter(c -> Files.exists(Paths.get(c)))
            .findFirst().orElse(null);
    }

    private String firstExecutable(List<String> candidates) {
        return candidates.stream()
            .filter(c -> {
                Path p = Paths.get(c);
                return Files.exists(p) && Files.isExecutable(p);
            })
            .findFirst().orElse(null);
    }

    // -------------------------------------------------------------------------
    // Privé — utilitaires
    // -------------------------------------------------------------------------

    private void assertEnabled() {
        if (!props.getLibreoffice().isEnabled()) {
            throw new IllegalStateException(
                "LibreOffice désactivé. Activez-le via document.xlsx.libreoffice.enabled=true");
        }
    }

    private int timeout() {
        return props.getLibreoffice().getTimeoutSeconds();
    }

    private Process launchProcess(List<String> cmd, String binary) throws IOException {
        try {
            return new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new IOException(
                "LibreOffice introuvable. Installez LibreOffice ou configurez "
                + YAML_PATH_KEY + ". Commande=" + binary, e);
        }
    }

    private Path resolvePdfPath(Path outDir, String baseName, String output)
            throws IOException {

        Path candidate = outDir.resolve(baseName + ".pdf");
        if (Files.exists(candidate)) return candidate;

        try (var stream = Files.list(outDir)) {
            Optional<Path> found = stream
                .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                .findFirst();
            if (found.isPresent()) return found.get();
        }

        throw new IOException("PDF non généré par LibreOffice. Output=" + output);
    }

    private void cleanup(Path inputXlsx, Path outDir, Path tempDir) {
        try {
            Files.deleteIfExists(inputXlsx);
            if (Files.exists(outDir)) {
                try (var s = Files.list(outDir)) {
                    s.forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (IOException ignored) {}
                    });
                }
                Files.deleteIfExists(outDir);
            }
            Files.deleteIfExists(tempDir);
        } catch (Exception e) {
            log.warn("⚠️ [LibreOffice] Erreur cleanup : {}", e.getMessage());
        }
    }

    private String readAll(InputStream in) {
        try (in) { return new String(in.readAllBytes()); }
        catch (Exception e) { return ""; }
    }
}