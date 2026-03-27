# Contract: File Validation & Type Detection

**Package**: `com.exemple.nexrag.service.rag.ingestion.util`

---

## `FileTypeDetector` Interface

```java
public interface FileTypeDetector {
    /**
     * Détecte le type MIME réel d'un fichier en inspectant ses magic bytes.
     * @param content octets bruts du fichier (non null)
     * @return type MIME (jamais null; "application/octet-stream" si inconnu)
     */
    String detect(byte[] content);
}
```

**Behavioural contract**:
- MUST inspect magic bytes, not the filename or declared content type.
- MUST return `"text/plain"` for a file renamed from `.txt` to `.pdf`.
- MUST return `"application/pdf"` for a valid PDF regardless of declared type.
- MUST NOT throw any exception for any input, including empty byte array.

---

## `FileValidator` Interface

```java
public interface FileValidator {
    /**
     * Valide un fichier uploadé avant traitement.
     * @param file fichier multipart (non null)
     * @throws FileSizeExceededException si la taille dépasse maxSizeBytes
     * @throws InvalidFileTypeException si l'extension n'est pas autorisée ou si le fichier est vide/null
     */
    void validate(MultipartFile file)
        throws FileSizeExceededException, InvalidFileTypeException;
}
```

**Behavioural contract**:
- MUST throw `InvalidFileTypeException` immediately for null or zero-byte file.
- MUST throw `FileSizeExceededException(actualSize, maxSize)` when `file.getSize() > maxSizeBytes`.
- `FileSizeExceededException.getMessage()` MUST contain the actual file size as a string.
- MUST throw `InvalidFileTypeException(extension)` when extension not in allowlist.
- Check order: null/empty → size → extension (fail fast).

---

## `MetadataSanitizer` Interface

```java
public interface MetadataSanitizer {
    /**
     * Supprime les métadonnées sensibles (EXIF GPS) des fichiers image.
     * @param imageBytes octets de l'image (non null)
     * @param mimeType type MIME détecté (non null)
     * @return octets sans métadonnées GPS; identique à l'entrée si non-image
     */
    byte[] sanitize(byte[] imageBytes, String mimeType);
}
```

**Behavioural contract** (Phase 1 scope — images only):
- MUST strip GPS EXIF fields from JPEG (`image/jpeg`) and PNG (`image/png`).
- MUST return the input bytes unchanged (no copy) for non-image MIME types.
- MUST NOT throw for any MIME type — non-image types are a no-op.
- After sanitisation, re-reading EXIF MUST show no GpsDirectory.

---

## Exceptions

### `FileSizeExceededException`
```java
public class FileSizeExceededException extends RuntimeException {
    private final long actualSize;
    private final long maxSize;
    // getMessage() MUST include actualSize as a numeric string
}
```

### `InvalidFileTypeException`
```java
public class InvalidFileTypeException extends RuntimeException {
    private final String extension;   // e.g. "exe", "bat"
}
```
