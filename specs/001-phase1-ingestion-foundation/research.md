# Research: Phase 1 — Ingestion Foundation Tests

**Branch**: `001-phase1-ingestion-foundation` | **Date**: 2026-03-26

---

## Decision 1: MIME-Type Detection Library

**Decision**: Use Apache Tika (`Tika.detect(byte[])`) already present in `pom.xml`
via `langchain4j-document-parser-apache-tika`.

**Rationale**: Tika inspects magic bytes natively, covers all MIME types present in
the ingestion pipeline (PDF, DOCX, XLSX, JPEG, PNG, plain text), and avoids adding
a new dependency. `FileTypeDetector` wraps `new Tika().detect(bytes)` — a one-liner.

**Alternatives considered**:
- Custom magic-byte lookup table: fragile, unmaintainable for 10+ file types.
- `java.net.URLConnection.guessContentTypeFromStream`: limited coverage, deprecated
  behaviour in newer JDKs.

**Test impact**: `FileTypeDetectorSpec` creates `byte[]` fixtures from real file headers
(e.g., `%PDF-` for PDF, `PK\x03\x04` for DOCX/ZIP) and asserts the returned MIME string.

---

## Decision 2: SHA-256 Hash Computation

**Decision**: Java standard library `MessageDigest.getInstance("SHA-256")`.
No external dependency required.

**Rationale**: SHA-256 via `MessageDigest` is available on every JDK 8+ platform,
produces a 64-character hex string when hex-encoded, and has zero transitive dependency
risk. `HashComputer` is a pure function: `byte[] → String`.

**Hex encoding**: `HexFormat.of().formatHex(digest)` (Java 17+, available on Java 21).
Alternatively `org.apache.commons.codec.binary.Hex.encodeHexString` if already on classpath.

**Test impact**: `HashComputerSpec` uses known vectors (e.g., empty-byte SHA-256 =
`e3b0c44298fc1c149afb...`) to assert determinism. Two identical byte arrays MUST produce
the same hash; different byte arrays MUST produce different hashes.

---

## Decision 3: EXIF Metadata Stripping

**Decision**: `metadata-extractor` library (`com.drewnoakes:metadata-extractor:2.19.0`)
for reading EXIF; rewrite-without-GPS via `Apache Commons Imaging` (Sanselan) for the
actual byte manipulation.

**Rationale**: `metadata-extractor` is the de-facto standard for reading EXIF/IPTC/XMP
from JPEG/PNG in Java. For stripping GPS fields and re-encoding the JPEG without them,
Apache Commons Imaging provides `JpegRewriter`. Both libraries are well-maintained.

**Scope**: Phase 1 handles JPEG/PNG (image EXIF) only. PDF/DOCX metadata is out of scope
— handled by ingestion strategies in Phase 2.

**Alternatives considered**:
- `Apache Tika` for EXIF reading: supported but Tika does not provide a write/rewrite API.
- `ImageIO + JAI`: low-level, no EXIF strip support out of the box.

**Test impact**: `MetadataSanitizerSpec` uses a tiny JPEG fixture with GPS EXIF bytes
embedded. After sanitisation, `metadata-extractor` is called again to verify no GPS
directory remains.

---

## Decision 4: ClamAV INSTREAM Protocol

**Decision**: Implement `ClamAvSocketClientImpl` using raw `java.net.Socket` with the
documented INSTREAM binary protocol.

**Protocol summary**:
```
1. Open TCP socket to ClamAV host:port (default 3310)
2. Send: b"zINSTREAM\0"          (command, null-terminated)
3. For each chunk of file data:
     Send: [4-byte big-endian chunk length][chunk bytes]
4. Send: [4 zero bytes]           (stream terminator)
5. Read response line: "stream: OK\n"  or  "stream: <ThreatName> FOUND\n"
6. Close socket
```

**Timeout**: Socket `setSoTimeout(timeoutMs)` before sending; `timeoutMs` comes from
`ClamAvProperties`. On `SocketTimeoutException`, throw `AntivirusUnavailableException`.

**Alternatives considered**:
- ClamAV REST (clamdClient): adds HTTP dependency; ClamAV in this stack is socket-only.
- clamav4j library: adds unmaintained dependency; direct socket is 30 lines.

---

## Decision 5: ServerSocket Stub for ClamAvSocketClientSpec

**Decision**: In `ClamAvSocketClientSpec`, use a `java.net.ServerSocket(0)` (ephemeral
port) started in `@BeforeEach`, accept one connection per test, write a fixed response
string, and close in `@AfterEach`.

**Pattern**:
```java
@BeforeEach
void startStubServer() throws IOException {
    stubServer = new ServerSocket(0);         // OS picks free port
    int port = stubServer.getLocalPort();
    clientProperties = new ClamAvProperties("localhost", port, 5000);
    client = new ClamAvSocketClientImpl(clientProperties);
    // Start thread to accept connection and reply
    Thread.ofVirtual().start(() -> {
        try (Socket conn = stubServer.accept();
             var out = conn.getOutputStream()) {
            out.write("stream: OK\n".getBytes());
        } catch (IOException ignored) {}
    });
}

@AfterEach
void stopStubServer() throws IOException {
    stubServer.close();
}
```

**Rationale**: No framework needed, stays under 500 ms, keeps tests fully isolated.
Virtual threads (Java 21) prevent test-thread blocking on `accept()`.

**Alternatives considered**:
- Mockito mock of `ClamAvSocketClient` interface: correct for `AntivirusGuardSpec`;
  but `ClamAvSocketClientSpec` must test the client's own protocol logic.
- WireMock TCP proxy extension: heavyweight, HTTP-oriented, not designed for INSTREAM.

---

## Decision 6: JaCoCo Coverage Gate Configuration

**Decision**: Configure JaCoCo Maven plugin in `pom.xml` with two rules:
1. Global rule: ≥80% line coverage AND ≥80% branch coverage on all classes in
   `com.exemple.nexrag.service.rag.ingestion.**`.
2. Per-class 100% rule on `AntivirusGuard`, `HashComputer`, `DeduplicationService`
   (BRANCH counter, MINIMUM 1.0).

**Maven plugin snippet** (add to `nex-rag/pom.xml` under `<build><plugins>`):
```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>prepare-agent</id>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>check-coverage</id>
      <phase>verify</phase>
      <goals><goal>check</goal></goals>
      <configuration>
        <rules>
          <rule>
            <element>PACKAGE</element>
            <includes>
              <include>com/exemple/nexrag/service/rag/ingestion/**</include>
            </includes>
            <limits>
              <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
              <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
            </limits>
          </rule>
          <rule>
            <element>CLASS</element>
            <includes>
              <include>com/exemple/nexrag/service/rag/ingestion/security/AntivirusGuard</include>
              <include>com/exemple/nexrag/service/rag/ingestion/deduplication/file/HashComputer</include>
              <include>com/exemple/nexrag/service/rag/ingestion/deduplication/file/DeduplicationService</include>
            </includes>
            <limits>
              <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>1.0</minimum></limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**Rationale**: `verify` phase runs after `test`, so the gate fires on `./mvnw verify`
(CI command) but not on `./mvnw test -DskipTests`. This allows developers to run fast
tests locally and only enforce gates in CI.
