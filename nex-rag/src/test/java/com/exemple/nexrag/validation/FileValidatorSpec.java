package com.exemple.nexrag.validation;


import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import com.exemple.nexrag.constant.FileSizeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires de {@link FileValidator}.
 *
 * Clean code  : tests nommés en français métier via @DisplayName.
 *               Structure Given/When/Then explicite.
 *               @Nested regroupe les cas par comportement testé.
 *               Pas de mocks inutiles — MockMultipartFile suffit.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : FileValidator — Validation taille et extension")
class FileValidatorSpec {

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileValidator();
    }

    // =========================================================================
    // validate(MultipartFile)
    // =========================================================================

    @Nested
    @DisplayName("validate() — fichier unique")
    class ValidateFile {

        @Test
        @DisplayName("accepte un fichier valide")
        void shouldPassForValidFile() {
            // Given
            MultipartFile file = validFile("document.pdf", 1_000);

            // When / Then — aucune exception attendue
            assertThatNoException().isThrownBy(() -> validator.validate(file));
        }

        @Test
        @DisplayName("rejette un fichier null")
        void shouldRejectNullFile() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(null))
                .withMessageContaining("Fichier vide ou absent");
        }

        @Test
        @DisplayName("rejette un fichier vide (0 octet)")
        void shouldRejectEmptyFile() {
            // Given
            MultipartFile file = emptyFile("vide.txt");

            // When / Then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(file))
                .withMessageContaining("Fichier vide ou absent");
        }

        @Test
        @DisplayName("rejette un fichier sans nom")
        void shouldRejectFileWithoutName() {
            // Given — filename vide produit par MockMultipartFile("")
            MultipartFile file = new MockMultipartFile(
                "file", "", "application/pdf", new byte[]{1, 2, 3}
            );

            // When / Then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(file))
                .withMessageContaining("Nom de fichier absent");
        }

        @Test
        @DisplayName("rejette un fichier dont la taille dépasse le maximum")
        void shouldRejectOversizedFile() {
            // Given — 1 octet au-dessus de la limite
            long oversizedBytes = FileSizeConstants.MAX_FILE_SIZE_BYTES + 1;
            MultipartFile file  = fileWithSize("gros.pdf", oversizedBytes);

            // When / Then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(file))
                .withMessageContaining("Fichier trop volumineux")
                .withMessageContaining(String.valueOf(FileSizeConstants.MAX_FILE_SIZE_MB));
        }

        @Test
        @DisplayName("accepte un fichier dont la taille est exactement au maximum")
        void shouldAcceptFileAtExactMaxSize() {
            // Given — exactement à la limite
            MultipartFile file = fileWithSize("limite.pdf", FileSizeConstants.MAX_FILE_SIZE_BYTES);

            // When / Then
            assertThatNoException().isThrownBy(() -> validator.validate(file));
        }

        @Test
        @DisplayName("le message d'erreur inclut la taille réelle du fichier en MB")
        void shouldIncludeActualSizeInErrorMessage() {
            // Given — relatif à la constante pour rester cohérent quelle que soit l'unité
            long oversizedBytes = FileSizeConstants.MAX_FILE_SIZE_BYTES + 1;
            long displayedMb    = oversizedBytes / 1_000_000; // même calcul que FileValidator
            MultipartFile file  = fileWithSize("trop-gros.zip", oversizedBytes);

            // When / Then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(file))
                .withMessageContaining("Fichier trop volumineux")
                .withMessageContaining(String.valueOf(displayedMb));
        }
    }

    // =========================================================================
    // validateBatch(List<MultipartFile>)
    // =========================================================================

    @Nested
    @DisplayName("validateBatch() — liste de fichiers")
    class ValidateBatch {

        @Test
        @DisplayName("accepte une liste contenant des fichiers valides")
        void shouldPassForValidBatch() {
            // Given
            List<MultipartFile> files = List.of(
                validFile("a.pdf",  1_000),
                validFile("b.docx", 2_000)
            );

            // When / Then
            assertThatNoException().isThrownBy(() -> validator.validateBatch(files));
        }

        @Test
        @DisplayName("rejette une liste null")
        void shouldRejectNullList() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validateBatch(null))
                .withMessageContaining("Aucun fichier fourni");
        }

        @Test
        @DisplayName("rejette une liste vide")
        void shouldRejectEmptyList() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validateBatch(List.of()))
                .withMessageContaining("Aucun fichier fourni");
        }

        @Test
        @DisplayName("rejette si un fichier du batch est vide")
        void shouldRejectBatchContainingEmptyFile() {
            // Given — un fichier valide + un fichier vide
            List<MultipartFile> files = List.of(
                validFile("valide.pdf", 1_000),
                emptyFile("invalide.txt")
            );

            // When / Then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validateBatch(files))
                .withMessageContaining("Fichier vide ou absent");
        }

        @Test
        @DisplayName("rejette si un fichier du batch dépasse la taille maximale")
        void shouldRejectBatchContainingOversizedFile() {
            // Given
            List<MultipartFile> files = List.of(
                validFile("ok.pdf", 1_000),
                fileWithSize("gros.zip", FileSizeConstants.MAX_FILE_SIZE_BYTES + 1)
            );

            // When / Then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validateBatch(files))
                .withMessageContaining("Fichier trop volumineux");
        }
    }

    // =========================================================================
    // Helpers — fabriques de fichiers de test
    // =========================================================================

    /**
     * Fichier avec contenu réel de {@code sizeBytes} octets.
     */
    private static MultipartFile validFile(String filename, int sizeBytes) {
        byte[] content = new byte[sizeBytes];
        content[0] = 1; // non-vide
        return new MockMultipartFile("file", filename, "application/octet-stream", content);
    }

    /**
     * Fichier vide (0 octet).
     */
    private static MultipartFile emptyFile(String filename) {
        return new MockMultipartFile("file", filename, "text/plain", new byte[0]);
    }

    /**
     * Fichier dont {@code getSize()} retourne exactement {@code sizeBytes}
     * sans allouer la mémoire correspondante — nécessaire pour tester les
     * limites de taille (ex : 100 MB) sans saturer la JVM pendant les tests.
     */
    private static MultipartFile fileWithSize(String filename, long sizeBytes) {
        return new MockMultipartFile("file", filename, "application/octet-stream", new byte[0]) {
            @Override public long    getSize()  { return sizeBytes; }
            @Override public boolean isEmpty()  { return false;     }
            @Override public byte[]  getBytes() { return new byte[]{1}; }
        };
    }
}





// package com.exemple.nexrag.service.rag.ingestion.util;

// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.springframework.mock.web.MockMultipartFile;

// import static org.assertj.core.api.Assertions.assertThatCode;
// import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @ExtendWith(MockitoExtension.class)
// @DisplayName("Spec : FileValidator — Validation taille et extension")
// class FileValidatorSpec {

//     private final FileValidator validator = new FileValidator();

//     @Test
//     @DisplayName("DOIT passer sans exception pour un fichier PDF valide")
//     void devraitAccepterFichierPdfValide() {
//         MockMultipartFile file = new MockMultipartFile(
//             "file", "document.pdf", "application/pdf", new byte[100]);
//         assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
//     }

//     @Test
//     @DisplayName("DOIT lever IllegalArgumentException pour un fichier null")
//     void devraitRefuserFichierNull() {
//         assertThatThrownBy(() -> validator.validate(null))
//             .isInstanceOf(IllegalArgumentException.class);
//     }

//     @Test
//     @DisplayName("DOIT lever IllegalArgumentException pour un fichier vide (0 bytes)")
//     void devraitRefuserFichierVide() {
//         MockMultipartFile empty = new MockMultipartFile(
//             "file", "vide.pdf", "application/pdf", new byte[0]);
//         assertThatThrownBy(() -> validator.validate(empty))
//             .isInstanceOf(IllegalArgumentException.class);
//     }

//     @Test
//     @DisplayName("DOIT lever IllegalArgumentException quand la taille dépasse 50 MB")
//     void devraitRefuserFichierTropGrand() {
//         byte[] bigContent = new byte[51 * 1024 * 1024]; // 51 MB
//         MockMultipartFile big = new MockMultipartFile(
//             "file", "gros.pdf", "application/pdf", bigContent);
//         assertThatThrownBy(() -> validator.validate(big))
//             .isInstanceOf(IllegalArgumentException.class)
//             .hasMessageContaining("bytes");
//     }

//     @Test
//     @DisplayName("DOIT lever IllegalArgumentException pour extension .exe non autorisée")
//     void devraitRefuserExtensionNonAutorisee() {
//         MockMultipartFile exe = new MockMultipartFile(
//             "file", "malware.exe", "application/octet-stream", new byte[100]);
//         assertThatThrownBy(() -> validator.validate(exe))
//             .isInstanceOf(IllegalArgumentException.class)
//             .hasMessageContaining("exe");
//     }

//     @Test
//     @DisplayName("DOIT accepter les extensions docx et xlsx")
//     void devraitAccepterExtensionsOffice() {
//         MockMultipartFile docx = new MockMultipartFile(
//             "file", "rapport.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
//             new byte[200]);
//         MockMultipartFile xlsx = new MockMultipartFile(
//             "file", "tableau.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
//             new byte[200]);

//         assertThatCode(() -> validator.validate(docx)).doesNotThrowAnyException();
//         assertThatCode(() -> validator.validate(xlsx)).doesNotThrowAnyException();
//     }
// }


