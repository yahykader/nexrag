package com.exemple.nexrag.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec unitaire de {@link FileSignatureValidator}.
 *
 * SRP : valide uniquement la logique de signature (magic bytes) et le blocage
 *       des extensions dangereuses — pas de logique HTTP.
 * DIP : instanciation directe sans contexte Spring ; aucune dépendance externe.
 */
@DisplayName("Spec : FileSignatureValidator — Validation des signatures de fichiers (magic bytes)")
class FileSignatureValidatorSpec {

    private FileSignatureValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileSignatureValidator();
    }

    // -------------------------------------------------------------------------
    // AC-23.1 — Extension dangereuse → SecurityException contenant "EXE" + "dangereuse"
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT lever SecurityException pour une extension dangereuse (.exe)")
    void devraitLeverSecurityExceptionPourExtensionDangereuse() throws Exception {
        MockMultipartFile exeFile = new MockMultipartFile(
            "file", "malware.exe", "application/octet-stream",
            new byte[]{0x4D, 0x5A, 0x00, 0x00}  // MZ header
        );

        assertThatThrownBy(() -> validator.validate(exeFile, "exe"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("EXE")
            .hasMessageContaining("dangereuse");
    }

    // -------------------------------------------------------------------------
    // AC-23.2 — Magic bytes non correspondants → SecurityException("Signature invalide...")
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT lever SecurityException quand les magic bytes ne correspondent pas à l'extension")
    void devraitLeverSecurityExceptionSiMagicBytesNonCorrespondants() throws Exception {
        // PNG bytes (89 50 4E 47) déclarés comme .pdf
        MockMultipartFile disguisedFile = new MockMultipartFile(
            "file", "malicious.pdf", "application/pdf",
            new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        );

        assertThatThrownBy(() -> validator.validate(disguisedFile, "pdf"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Signature invalide");
    }

    // -------------------------------------------------------------------------
    // AC-23.3 — Extension sans signature connue (.txt) → aucune exception
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT ignorer la validation de signature pour les extensions sans magic bytes connus (.txt)")
    void devraitIgnorerValidationSignaturePourExtensionsSansSignature() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile(
            "file", "readme.txt", "text/plain",
            "Contenu texte quelconque".getBytes()
        );

        assertThatNoException().isThrownBy(() -> validator.validate(txtFile, "txt"));
    }

    // -------------------------------------------------------------------------
    // AC-23.4 — Magic bytes PDF (%PDF) → detectRealType() retourne "pdf"
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT détecter le type pdf par ses magic bytes (%PDF)")
    void devraitDecouvriTypePdfParMagicBytes() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
            "file", "document.pdf", "application/pdf",
            new byte[]{0x25, 0x50, 0x44, 0x46}  // %PDF
        );

        String detected = validator.detectRealType(pdfFile);

        assertThat(detected).isEqualTo("pdf");
    }

    // -------------------------------------------------------------------------
    // AC-23.5 — validateComplete(.exe) → ValidationResult(isValid=false)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner un ValidationResult invalide pour une extension .exe")
    void devraitRetournerValidationResultatInvalidePourExe() throws Exception {
        MockMultipartFile exeFile = new MockMultipartFile(
            "file", "virus.exe", "application/octet-stream",
            new byte[]{0x4D, 0x5A, 0x00, 0x00}
        );

        FileSignatureValidator.ValidationResult result = validator.validateComplete(exeFile, "exe");

        assertThat(result.isValid()).isFalse();
    }

    // -------------------------------------------------------------------------
    // AC-23.6 — Bytes ZIP + extension .docx → isExtensionMatching() = true
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT accepter un .docx dont le type réel détecté est ZIP (OOXML)")
    void devraitAccepterDocxDontTypeDetecteEstZip() throws Exception {
        // DOCX, XLSX, PPTX partagent la signature ZIP (PK\x03\x04)
        MockMultipartFile docxFile = new MockMultipartFile(
            "file", "report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00}
        );

        boolean matching = validator.isExtensionMatching(docxFile, "docx");

        assertThat(matching).isTrue();
    }

    // -------------------------------------------------------------------------
    // AC-23.7 — Fichier trop court pour PDF (1 byte) → SecurityException("trop court")
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT lever SecurityException quand le fichier est trop court pour contenir la signature attendue")
    void devraitLeverSecurityExceptionSiFichierTropCourt() throws Exception {
        MockMultipartFile shortFile = new MockMultipartFile(
            "file", "truncated.pdf", "application/pdf",
            new byte[]{0x25}  // 1 byte seulement, PDF exige 4 bytes de signature
        );

        assertThatThrownBy(() -> validator.validate(shortFile, "pdf"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("trop court");
    }
}
