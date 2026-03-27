# Data Model: Phase 1 — Ingestion Foundation Tests

**Branch**: `001-phase1-ingestion-foundation` | **Date**: 2026-03-26

All entities below are Java types in `com.exemple.nexrag.service.rag.ingestion`.
Records are immutable DTOs. Interfaces define the dependency-injection contracts
that test classes will mock via Mockito.

---

## Value Objects & Records

### `ClamAvScanResult`
```
record ClamAvScanResult(ScanStatus status, @Nullable String threatName)

enum ScanStatus { CLEAN, INFECTED, ERROR }
```
- `status`: result of the ClamAV scan (never null)
- `threatName`: virus name when `status == INFECTED`; null otherwise
- Invariant: `threatName != null` implies `status == INFECTED`

### `ClamAvProperties`
```
record ClamAvProperties(String host, int port, int timeoutMs)
```
- `host`: ClamAV daemon hostname (e.g., `localhost`, `clamav`)
- `port`: daemon TCP port (default 3310)
- `timeoutMs`: socket read timeout in milliseconds; used for both connection and scan stall

### `FileValidationProperties`
```
record FileValidationProperties(long maxSizeBytes, List<String> allowedExtensions)
```
- `maxSizeBytes`: file size ceiling in bytes (e.g., `52_428_800` for 50 MB)
- `allowedExtensions`: lowercase allowlist (e.g., `["pdf", "docx", "xlsx", "txt", "jpg", "png"]`)

---

## Interfaces (Dependency-Injection Contracts)

### `FileTypeDetector`
```
interface FileTypeDetector {
    String detect(byte[] content);   // returns MIME type, e.g. "application/pdf"
}
```
- Never returns null; returns `"application/octet-stream"` for unrecognised bytes.
- Implementation: wraps `new Tika().detect(byte[])`.

### `FileValidator`
```
interface FileValidator {
    void validate(MultipartFile file)
        throws FileSizeExceededException, InvalidFileTypeException;
}
```
- Throws `FileSizeExceededException(actualSize, maxSize)` if `file.getSize() > maxSizeBytes`.
- Throws `InvalidFileTypeException(extension)` if extension not in allowlist.
- Throws `InvalidFileTypeException` immediately if file is null or empty (0 bytes).

### `MetadataSanitizer`
```
interface MetadataSanitizer {
    byte[] sanitize(byte[] imageBytes, String mimeType);
}
```
- Scope: JPEG (`image/jpeg`) and PNG (`image/png`) only in Phase 1.
- Returns a new byte array with GPS EXIF fields removed.
- Returns input unchanged if `mimeType` is not an image type (no-op, no exception).

### `HashComputer`
```
interface HashComputer {
    String compute(byte[] content);   // returns SHA-256 hex string (64 chars)
}
```
- Pure function: identical inputs ALWAYS produce identical outputs.
- Empty byte array maps to `"e3b0c44298fc1c149afb4c8996fb92427ae41e4649b934ca495991b7852b855"`.

### `DeduplicationStore`
```
interface DeduplicationStore {
    boolean exists(String hash);
    void    save(String hash);
}
```
- `exists(hash)`: read-only, idempotent — MUST NOT write to Redis.
- `save(hash)`: writes the hash to Redis; subsequent `exists(hash)` calls return `true`.

### `DeduplicationService`
```
interface DeduplicationService {
    boolean isDuplicate(byte[] fileContent);
}
```
- Computes hash via `HashComputer`, checks via `DeduplicationStore.exists()`.
- Does NOT call `save()` — registration after successful ingestion is the orchestrator's job.

### `TextNormalizer`
```
interface TextNormalizer {
    String normalize(String text);
}
```
- Pure function: lowercase → trim → remove diacritics (NFD decomposition + strip combining chars).
- `normalize(null)` returns empty string; `normalize("  ")` returns `""`.

### `TextLocalCache`
```
class TextLocalCache {
    boolean contains(String normalizedText);
    void    register(String normalizedText);
    void    clear();
}
```
- Batch-scoped (one instance per ingestion batch, not a Spring singleton).
- Backed by `HashSet<String>` internally.
- `clear()` empties the in-memory set only; no effect on Redis.

### `TextDeduplicationService`
```
interface TextDeduplicationService {
    boolean isDuplicate(String chunk);
}
```
- Normalises chunk via `TextNormalizer`, checks local cache first, then Redis store.
- Does NOT register the chunk on first call; registration is the caller's responsibility.

### `ClamAvSocketClient`
```
interface ClamAvSocketClient {
    ClamAvScanResult scan(InputStream content)
        throws AntivirusUnavailableException;
}
```
- Throws `AntivirusUnavailableException` on connection refused or socket timeout.
- Implementation: `ClamAvSocketClientImpl` uses raw TCP socket + INSTREAM protocol.

### `ClamAvResponseParser`
```
class ClamAvResponseParser {   // concrete, stateless — no interface needed
    ClamAvScanResult parse(String rawResponse);
}
```
- Pure function: parses daemon response string.
- `"stream: OK\n"` → `ClamAvScanResult(CLEAN, null)`
- `"stream: X FOUND\n"` → `ClamAvScanResult(INFECTED, "X")`
- Any other format → `ClamAvScanResult(ERROR, rawResponse)`

---

## Exceptions

| Exception | Thrown by | Key fields |
|-----------|-----------|------------|
| `FileSizeExceededException` | `FileValidator` | `actualSize`, `maxSize` |
| `InvalidFileTypeException` | `FileValidator` | `extension` |
| `VirusFoundException` | `AntivirusGuard` | `threatName`, `fileName` |
| `AntivirusUnavailableException` | `AntivirusGuard`, `ClamAvSocketClient` | `cause` |

---

## State Transitions: `DeduplicationStore`

```
[not seen]  --save(hash)-->  [registered]
[registered]  --exists(hash)-->  true   (idempotent)
[not seen]   --exists(hash)-->  false
```

## State Transitions: `TextLocalCache`

```
[empty]  --register(text)-->  [contains text]
[contains text]  --contains(text)-->  true
[contains text]  --clear()-->  [empty]
```
