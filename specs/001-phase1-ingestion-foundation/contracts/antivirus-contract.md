# Contract: Antivirus Security Guard

**Package**: `com.exemple.nexrag.service.rag.ingestion.security`

---

## `ClamAvSocketClient` Interface

```java
public interface ClamAvSocketClient {
    /**
     * Scanne un flux de données via le protocole ClamAV INSTREAM.
     * @param content flux de données du fichier
     * @return résultat du scan (CLEAN, INFECTED, ou ERROR)
     * @throws AntivirusUnavailableException si connexion refusée ou timeout socket
     */
    ClamAvScanResult scan(InputStream content)
        throws AntivirusUnavailableException;
}
```

**Behavioural contract**:
- MUST be an interface so `AntivirusGuard` can receive it via constructor injection (DIP).
- Implementation (`ClamAvSocketClientImpl`) uses raw TCP socket + ClamAV INSTREAM protocol.
- On `ConnectException` (connection refused): MUST throw `AntivirusUnavailableException`.
- On `SocketTimeoutException` (scan stall > `timeoutMs`): MUST throw `AntivirusUnavailableException`.
- `AntivirusUnavailableException` wraps the original `IOException` as cause.

### INSTREAM Protocol (implementation detail for `ClamAvSocketClientSpec`):
```
→ send:  b"zINSTREAM\0"
→ send:  [4-byte BE int: chunk length][chunk bytes]  (repeat for each chunk)
→ send:  [0x00 0x00 0x00 0x00]  (end-of-stream terminator)
← recv:  "stream: OK\n"                          → ScanStatus.CLEAN
         "stream: <ThreatName> FOUND\n"           → ScanStatus.INFECTED
         any other format                         → ScanStatus.ERROR
```

---

## `ClamAvResponseParser` (concrete, stateless)

```java
public class ClamAvResponseParser {
    /**
     * Interprète la réponse textuelle du daemon ClamAV.
     * @param rawResponse réponse brute (non null)
     * @return résultat du scan
     */
    public ClamAvScanResult parse(String rawResponse);
}
```

**Behavioural contract**:
- Pure function — no state, no side effects.
- `"stream: OK\n"` → `ClamAvScanResult(CLEAN, null)`
- `"stream: Eicar-Test-Signature FOUND\n"` → `ClamAvScanResult(INFECTED, "Eicar-Test-Signature")`
- Any unrecognised format → `ClamAvScanResult(ERROR, rawResponse)`
- MUST NOT throw for any input string.

---

## `AntivirusGuard` (concrete, fail-secure)

```java
public class AntivirusGuard {
    /**
     * Vérifie qu'un fichier est sain avant ingestion.
     * Stratégie fail-secure : tout doute → exception.
     *
     * @param file fichier multipart à scanner
     * @throws VirusFoundException si le scan retourne INFECTED
     * @throws AntivirusUnavailableException si ClamAV est indisponible ou timeout
     */
    public void assertClean(MultipartFile file)
        throws VirusFoundException, AntivirusUnavailableException;
}
```

**Behavioural contract**:
- Depends on `ClamAvSocketClient` via constructor injection.
- `INFECTED` → throws `VirusFoundException(threatName, fileName)`.
- Connection refused → throws `AntivirusUnavailableException` (delegates from client).
- Socket timeout (scan stall) → throws `AntivirusUnavailableException` (delegates from client).
- `CLEAN` → returns normally, no exception.
- `ERROR` (parser returns ERROR) → throws `AntivirusUnavailableException` (fail-secure).

---

## `ClamAvHealthScheduler`

```java
public class ClamAvHealthScheduler {
    /**
     * @return true si le dernier health check a réussi
     */
    public boolean isHealthy();

    /**
     * Vérifie périodiquement la disponibilité de ClamAV.
     * Appelé automatiquement par le scheduler Spring.
     * Ne lève pas d'exception en cas d'échec.
     */
    @Scheduled(fixedDelayString = "${app.clamav.health-check-interval-ms:30000}")
    public void checkHealth();
}
```

**Behavioural contract**:
- `checkHealth()` MUST NOT throw even if ClamAV is unreachable.
- On failure: MUST log a warning (emoji `⚠️`) and set health status to `false`.
- On success: sets health status to `true`.
- `isHealthy()` reflects the outcome of the last `checkHealth()` invocation.

---

## Exceptions

### `VirusFoundException`
```java
public class VirusFoundException extends RuntimeException {
    private final String threatName;   // e.g. "Eicar-Test-Signature"
    private final String fileName;
    // getMessage() MUST contain both threatName and fileName
}
```

### `AntivirusUnavailableException`
```java
public class AntivirusUnavailableException extends RuntimeException {
    // wraps original IOException as cause
    // thrown for: connection refused, socket timeout, ERROR scan status
}
```
