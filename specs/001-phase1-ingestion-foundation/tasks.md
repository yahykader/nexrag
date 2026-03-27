---

description: "Phase 1 — Ingestion Foundation Unit Tests"
---

# Tasks: Phase 1 — Ingestion Foundation Tests
# (Utilitaires, Sécurité & Déduplication)

**Input**: Design documents from `specs/001-phase1-ingestion-foundation/`
**Prerequisites**: plan.md ✅ · spec.md ✅ · research.md ✅ · data-model.md ✅ · contracts/ ✅

> **LLM Implementation Note**: Every task below is self-contained. The exact class name,
> file path, method signatures, and `@DisplayName` text are provided. Production code
> lives under `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/`.
> Test code lives under `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/`.
> All test classes use `@ExtendWith(MockitoExtension.class)` and `@DisplayName` in French.
> See `data-model.md` and `contracts/` for full method signatures and invariants.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (no dependency on an incomplete task)
- **[Story]**: US1 = File Validation, US2 = File Deduplication, US3 = Text Dedup, US4 = Antivirus

---

## Phase 1: Setup

**Purpose**: Configure build tooling so coverage gates run in CI.

- [X] T001 Add JaCoCo Maven plugin to `nex-rag/pom.xml` — copy the full `<plugin>` block from `research.md` Decision 6 (two rules: 80% package gate + 100% branch gate on AntivirusGuard/HashComputer/DeduplicationService)
- [X] T002 [P] Verify JUnit 5, Mockito, and AssertJ are present in `nex-rag/pom.xml` under `<dependencies>` with `<scope>test</scope>`; add any that are missing (Spring Boot BOM manages versions — do NOT specify version numbers)
- [X] T003 [P] Create empty package directories so the compiler finds them: `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/util/`, `.../deduplication/file/`, `.../deduplication/text/`, `.../security/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Production value objects and exceptions that ALL user stories depend on.
These classes carry no business logic — they are pure data holders.

**⚠️ CRITICAL**: No Spec class can compile until the types it references exist.

- [ ] T004 [P] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/security/ScanStatus.java` — public enum with values `CLEAN`, `INFECTED`, `ERROR`
- [ ] T005 [P] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/security/ClamAvScanResult.java` — Java record: `public record ClamAvScanResult(ScanStatus status, @Nullable String threatName)`. Import `jakarta.annotation.Nullable`. No additional methods.
- [ ] T006 [P] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/security/ClamAvProperties.java` — Java record: `public record ClamAvProperties(String host, int port, int timeoutMs)`
- [ ] T007 [P] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/util/FileValidationProperties.java` — Java record: `public record FileValidationProperties(long maxSizeBytes, List<String> allowedExtensions)`. Import `java.util.List`.
- [ ] T008 [P] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/util/FileSizeExceededException.java` — `public class FileSizeExceededException extends RuntimeException` with fields `private final long actualSize` and `private final long maxSize`. Constructor: `FileSizeExceededException(long actualSize, long maxSize)` calls `super("Taille fichier " + actualSize + " dépasse le maximum " + maxSize)`. Add getters.
- [ ] T009 [P] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/util/InvalidFileTypeException.java` — `public class InvalidFileTypeException extends RuntimeException` with field `private final String extension`. Constructor: `InvalidFileTypeException(String extension)` calls `super("Extension non autorisée: " + extension)`. Add getter.
- [ ] T010 [P] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/security/VirusFoundException.java` — `public class VirusFoundException extends RuntimeException` with fields `private final String threatName` and `private final String fileName`. Constructor sets both; `super("Virus détecté: " + threatName + " dans " + fileName)`. Add getters.
- [ ] T011 [P] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/security/AntivirusUnavailableException.java` — `public class AntivirusUnavailableException extends RuntimeException`. Two constructors: `(String message)` and `(String message, Throwable cause)`.

**Checkpoint**: Run `./mvnw compile -pl nex-rag` — must compile with zero errors before continuing.

---

## Phase 3: US-1 — File Validation & Type Detection (Priority: P1) 🎯 MVP

**Goal**: Detect real MIME type by magic bytes; reject oversized and forbidden-extension files; strip GPS EXIF from images.
**Independent Test**: `./mvnw test -Dtest="FileTypeDetectorSpec,FileValidatorSpec,MetadataSanitizerSpec" -pl nex-rag`

### Production Interfaces — US-1

- [ ] T012 [P] [US1] Create interface `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/util/FileTypeDetector.java`:
  ```java
  public interface FileTypeDetector {
      String detect(byte[] content);
  }
  ```

- [ ] T013 [P] [US1] Create interface `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/util/FileValidator.java`:
  ```java
  public interface FileValidator {
      void validate(MultipartFile file)
          throws FileSizeExceededException, InvalidFileTypeException;
  }
  ```
  Import `org.springframework.web.multipart.MultipartFile`.

- [ ] T014 [P] [US1] Create interface `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/util/MetadataSanitizer.java`:
  ```java
  public interface MetadataSanitizer {
      byte[] sanitize(byte[] imageBytes, String mimeType);
  }
  ```

### Production Implementations — US-1

- [ ] T015 [US1] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/util/TikaFileTypeDetector.java` implementing `FileTypeDetector`. Inject no dependencies. `detect(byte[] content)`: create `new org.apache.tika.Tika()` and call `.detect(content)`. Return result. Never return null — return `"application/octet-stream"` if result is null.

- [ ] T016 [US1] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/util/DefaultFileValidator.java` implementing `FileValidator`. Constructor: `DefaultFileValidator(FileValidationProperties properties)`. `validate()` logic: (1) if file is null or `file.isEmpty()` → throw `InvalidFileTypeException("empty")`. (2) if `file.getSize() > properties.maxSizeBytes()` → throw `FileSizeExceededException(file.getSize(), properties.maxSizeBytes())`. (3) get extension from filename (after last `.`, lowercase); if not in `properties.allowedExtensions()` → throw `InvalidFileTypeException(extension)`.

- [ ] T017 [US1] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/util/ExifMetadataSanitizer.java` implementing `MetadataSanitizer`. Constructor: no args. `sanitize()` logic: if mimeType is not `"image/jpeg"` and not `"image/png"` → return `imageBytes` unchanged. Otherwise use `org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter` to copy JPEG bytes omitting GPS EXIF directory. Catch all exceptions and re-throw as `RuntimeException`.

### Test Classes — US-1

- [X] T018 [P] [US1] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/util/FileTypeDetectorSpec.java`:
  ```java
  @DisplayName("Spec : FileTypeDetector — Détection MIME par magic bytes")
  @ExtendWith(MockitoExtension.class)
  class FileTypeDetectorSpec {
      private final FileTypeDetector detector = new TikaFileTypeDetector();

      @Test
      @DisplayName("DOIT retourner text/plain quand bytes sont text même si extension .pdf")
      void shouldDetectPlainTextFromBytes() {
          byte[] textBytes = "Hello world".getBytes(StandardCharsets.UTF_8);
          assertThat(detector.detect(textBytes)).isEqualTo("text/plain");
      }

      @Test
      @DisplayName("DOIT retourner application/pdf pour un vrai PDF")
      void shouldDetectPdfFromMagicBytes() {
          byte[] pdfHeader = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);
          assertThat(detector.detect(pdfHeader)).contains("pdf");
      }

      @Test
      @DisplayName("DOIT retourner une valeur non-null pour un tableau vide")
      void shouldNotReturnNullForEmptyBytes() {
          assertThat(detector.detect(new byte[0])).isNotNull();
      }
  }
  ```

- [X] T019 [P] [US1] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/util/FileValidatorSpec.java`:
  ```java
  @DisplayName("Spec : FileValidator — Validation taille et extension")
  @ExtendWith(MockitoExtension.class)
  class FileValidatorSpec {
      @Mock private FileValidationProperties properties;
      @InjectMocks private DefaultFileValidator validator;

      // AC-1.2
      @Test
      @DisplayName("DOIT lever FileSizeExceededException quand fichier dépasse maxSize")
      void shouldRejectFileExceedingMaxSize() {
          when(properties.maxSizeBytes()).thenReturn(1024L);
          when(properties.allowedExtensions()).thenReturn(List.of("pdf","txt"));
          MockMultipartFile big = new MockMultipartFile("file","test.pdf","application/pdf",new byte[2048]);
          assertThatThrownBy(() -> validator.validate(big))
              .isInstanceOf(FileSizeExceededException.class)
              .hasMessageContaining("2048");
      }

      // AC-1.3
      @Test
      @DisplayName("DOIT lever InvalidFileTypeException pour extension .exe")
      void shouldRejectForbiddenExtension() {
          when(properties.maxSizeBytes()).thenReturn(10_000L);
          when(properties.allowedExtensions()).thenReturn(List.of("pdf","docx","txt"));
          MockMultipartFile exe = new MockMultipartFile("file","malware.exe","application/octet-stream",new byte[10]);
          assertThatThrownBy(() -> validator.validate(exe))
              .isInstanceOf(InvalidFileTypeException.class);
      }

      // Edge case: null file
      @Test
      @DisplayName("DOIT lever InvalidFileTypeException pour fichier null")
      void shouldRejectNullFile() {
          assertThatThrownBy(() -> validator.validate(null))
              .isInstanceOf(InvalidFileTypeException.class);
      }

      // Happy path
      @Test
      @DisplayName("DOIT passer sans exception pour fichier valide")
      void shouldPassValidFile() {
          when(properties.maxSizeBytes()).thenReturn(10_000L);
          when(properties.allowedExtensions()).thenReturn(List.of("pdf"));
          MockMultipartFile valid = new MockMultipartFile("file","doc.pdf","application/pdf",new byte[100]);
          assertThatCode(() -> validator.validate(valid)).doesNotThrowAnyException();
      }
  }
  ```
  Imports needed: `org.springframework.mock.web.MockMultipartFile`, `static org.assertj.core.api.Assertions.*`, `java.util.List`.

- [X] T020 [P] [US1] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/util/MetadataSanitizerSpec.java` with class-level `@DisplayName("Spec : MetadataSanitizer — Suppression EXIF GPS")` and `@ExtendWith(MockitoExtension.class)`. Field: `private final MetadataSanitizer sanitizer = new ExifMetadataSanitizer()`. Write these 3 test methods:
  - `shouldReturnInputUnchangedForNonImageMimeType()` — call `sanitizer.sanitize(new byte[]{1,2,3}, "application/pdf")`, assert result `== input` (same reference or same bytes).
  - `shouldReturnInputUnchangedForPng()` — for scope note: PNG sanitisation is a no-op in Phase 1 if Commons Imaging PNG rewrite is not implemented; assert no exception thrown.
  - `shouldNotThrowForEmptyImageBytes()` — call `sanitizer.sanitize(new byte[0], "image/jpeg")`, assert no exception (may return empty array).

- [X] T021 [P] [US1] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/util/FileUtilsSpec.java` with `@DisplayName("Spec : FileUtils — Utilitaires fichiers")` and `@ExtendWith(MockitoExtension.class)`. If a `FileUtils` utility class exists, add tests for each public static method. If `FileUtils` does not exist yet, create a placeholder test class with one passing test: `shouldExist()` annotated `@Test @DisplayName("DOIT exister")` that simply calls `assertThat(true).isTrue()` — this will be replaced when FileUtils is implemented.

- [X] T022 [P] [US1] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/util/InMemoryMultipartFileSpec.java` with `@DisplayName("Spec : InMemoryMultipartFile — Fichier multipart en mémoire")`. If `InMemoryMultipartFile` exists in production, test: (1) `getName()` returns provided name; (2) `getBytes()` returns provided bytes; (3) `isEmpty()` returns true for zero-length file. If it does not exist yet, create a placeholder similar to T021.

**Checkpoint US-1**: `./mvnw test -Dtest="FileTypeDetectorSpec,FileValidatorSpec,MetadataSanitizerSpec" -pl nex-rag` — all tests PASS.

---

## Phase 4: US-2 — File Deduplication (Priority: P1)

**Goal**: SHA-256 hash detection of binary-identical files; both read AND write paths tested.
**Independent Test**: `./mvnw test -Dtest="HashComputerSpec,DeduplicationStoreSpec,DeduplicationServiceSpec" -pl nex-rag`

### Production Interfaces — US-2

- [ ] T023 [P] [US2] Create interface `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/deduplication/file/HashComputer.java`:
  ```java
  public interface HashComputer {
      String compute(byte[] content);
  }
  ```

- [ ] T024 [P] [US2] Create interface `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/deduplication/file/DeduplicationStore.java`:
  ```java
  public interface DeduplicationStore {
      boolean exists(String hash);
      void save(String hash);
  }
  ```

- [ ] T025 [P] [US2] Create interface `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/deduplication/file/DeduplicationService.java`:
  ```java
  public interface DeduplicationService {
      boolean isDuplicate(byte[] fileContent);
  }
  ```

### Production Implementations — US-2

- [ ] T026 [US2] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/deduplication/file/Sha256HashComputer.java` implementing `HashComputer`. No constructor args. `compute(byte[] content)`: use `MessageDigest.getInstance("SHA-256")`, call `.digest(content)`, convert to hex with `HexFormat.of().formatHex(bytes)`. Return the 64-char hex string. Wrap `NoSuchAlgorithmException` in `RuntimeException`.

- [ ] T027 [US2] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/deduplication/file/RedisDeduplicationStore.java` implementing `DeduplicationStore`. Constructor: `RedisDeduplicationStore(RedisTemplate<String, String> redisTemplate, String keyPrefix)`. `exists(hash)`: call `redisTemplate.hasKey(keyPrefix + hash)`. `save(hash)`: call `redisTemplate.opsForValue().set(keyPrefix + hash, "1")`.

- [ ] T028 [US2] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/deduplication/file/DefaultDeduplicationService.java` implementing `DeduplicationService`. Constructor: `DefaultDeduplicationService(HashComputer hashComputer, DeduplicationStore store)`. `isDuplicate(byte[] fileContent)`: compute hash via `hashComputer.compute(fileContent)`, return `store.exists(hash)`. Do NOT call `store.save()` here.

### Test Classes — US-2

- [X] T029 [P] [US2] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/deduplication/file/HashComputerSpec.java`:
  ```java
  @DisplayName("Spec : HashComputer — Hash SHA-256 déterministe")
  @ExtendWith(MockitoExtension.class)
  class HashComputerSpec {
      private final HashComputer computer = new Sha256HashComputer();

      // AC-2.1
      @Test
      @DisplayName("DOIT produire le même hash pour deux tableaux identiques")
      void shouldProduceSameHashForIdenticalContent() {
          byte[] a = "hello".getBytes(StandardCharsets.UTF_8);
          byte[] b = "hello".getBytes(StandardCharsets.UTF_8);
          assertThat(computer.compute(a)).isEqualTo(computer.compute(b));
      }

      @Test
      @DisplayName("DOIT produire un hash de 64 caractères hex")
      void shouldProduceHashOf64HexChars() {
          assertThat(computer.compute("test".getBytes())).hasSize(64).matches("[0-9a-f]+");
      }

      @Test
      @DisplayName("DOIT produire le hash SHA-256 connu pour un tableau vide")
      void shouldComputeKnownHashForEmptyArray() {
          assertThat(computer.compute(new byte[0]))
              .isEqualTo("e3b0c44298fc1c149afb4c8996fb92427ae41e4649b934ca495991b7852b855");
      }

      @Test
      @DisplayName("DOIT produire des hashs différents pour des contenus différents")
      void shouldProduceDifferentHashesForDifferentContent() {
          assertThat(computer.compute("abc".getBytes()))
              .isNotEqualTo(computer.compute("xyz".getBytes()));
      }
  }
  ```

- [X] T030 [P] [US2] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/deduplication/file/DeduplicationStoreSpec.java` with `@DisplayName("Spec : DeduplicationStore — Persistance des hashs Redis")` and `@ExtendWith(MockitoExtension.class)`. Fields: `@Mock RedisTemplate<String,String> redisTemplate`, `@Mock ValueOperations<String,String> valueOps`, `private RedisDeduplicationStore store`. `@BeforeEach`: `when(redisTemplate.opsForValue()).thenReturn(valueOps); store = new RedisDeduplicationStore(redisTemplate, "dedup:");`. Write these tests:
  - `shouldReturnTrueWhenHashExists()` — `when(redisTemplate.hasKey("dedup:abc123")).thenReturn(true)` → `assertThat(store.exists("abc123")).isTrue()`
  - `shouldReturnFalseWhenHashAbsent()` — `when(redisTemplate.hasKey("dedup:missing")).thenReturn(false)` → `assertThat(store.exists("missing")).isFalse()`
  - `shouldSaveHashToRedis()` (AC-2.4 write path) — call `store.save("newhash")` → `verify(valueOps).set("dedup:newhash", "1")`
  - `shouldNotWriteOnExists()` — call `store.exists("abc")` → `verify(redisTemplate, never()).opsForValue()`; `verify(redisTemplate).hasKey(anyString())`

- [X] T031 [P] [US2] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/deduplication/file/DeduplicationServiceSpec.java` with `@DisplayName("Spec : DeduplicationService — Détection de doublons fichiers")` and `@ExtendWith(MockitoExtension.class)`. Fields: `@Mock HashComputer hashComputer`, `@Mock DeduplicationStore store`, `@InjectMocks DefaultDeduplicationService service`. Write these tests:
  - `shouldReturnTrueForDuplicateFile()` (AC-2.2) — `when(hashComputer.compute(any())).thenReturn("hash1")`, `when(store.exists("hash1")).thenReturn(true)` → `assertThat(service.isDuplicate(new byte[]{1})).isTrue()`
  - `shouldReturnFalseForNewFile()` — mock returns false → `assertThat(service.isDuplicate(new byte[]{2})).isFalse()`
  - `shouldNotCallSaveOnCheck()` (AC-2.3 idempotence) — call `isDuplicate()` twice → `verify(store, never()).save(anyString())`
  - `shouldDelegateHashComputation()` — verify `hashComputer.compute(content)` called with correct bytes

**Checkpoint US-2**: `./mvnw test -Dtest="HashComputerSpec,DeduplicationStoreSpec,DeduplicationServiceSpec" -pl nex-rag` — all tests PASS.

---

## Phase 5: US-3 — Text Chunk Deduplication (Priority: P2)

**Goal**: Session-local deduplication of text chunks; fresh `TextLocalCache` per batch.
**Independent Test**: `./mvnw test -Dtest="TextNormalizerSpec,TextLocalCacheSpec,TextDeduplicationServiceSpec" -pl nex-rag`

### Production Interfaces — US-3

- [ ] T032 [P] [US3] Create interface `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/deduplication/text/TextNormalizer.java`:
  ```java
  public interface TextNormalizer {
      String normalize(String text);
  }
  ```

- [ ] T033 [P] [US3] Create interface `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/deduplication/text/TextDeduplicationService.java`:
  ```java
  public interface TextDeduplicationService {
      boolean isDuplicate(String chunk);
  }
  ```

### Production Implementations — US-3

- [ ] T034 [US3] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/deduplication/text/DefaultTextNormalizer.java` implementing `TextNormalizer`. `normalize(String text)`: (1) if null or blank → return `""`. (2) `text.toLowerCase(Locale.ROOT)`. (3) `.strip()`. (4) NFD decomposition: `Normalizer.normalize(text, Normalizer.Form.NFD)` then remove chars matching `\p{Mn}` via regex. Return result. Import `java.text.Normalizer`.

- [ ] T035 [US3] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/deduplication/text/TextLocalCache.java`. NOT an interface — concrete class. Field: `private final Set<String> cache = new HashSet<>()`. Methods: `boolean contains(String normalizedText)` → `cache.contains(normalizedText)`. `void register(String normalizedText)` → `cache.add(normalizedText)`. `void clear()` → `cache.clear()`. No Spring annotations — this is a plain Java class.

- [ ] T036 [US3] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/deduplication/text/DefaultTextDeduplicationService.java` implementing `TextDeduplicationService`. Constructor: `DefaultTextDeduplicationService(TextNormalizer normalizer, TextLocalCache localCache)`. `isDuplicate(String chunk)`: (1) normalise via `normalizer.normalize(chunk)`. (2) return `localCache.contains(normalized)`. Do NOT register the chunk here.

### Test Classes — US-3

- [X] T037 [P] [US3] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/deduplication/text/TextNormalizerSpec.java` with `@DisplayName("Spec : TextNormalizer — Normalisation canonique")` and `@ExtendWith(MockitoExtension.class)`. Field: `private final TextNormalizer normalizer = new DefaultTextNormalizer()`. Write these tests:
  - `shouldNormalizeTwoVariantsIdentically()` (AC-3.1) — `assertThat(normalizer.normalize("Hello World")).isEqualTo(normalizer.normalize("  hello world  "))`
  - `shouldReturnEmptyStringForNull()` — `assertThat(normalizer.normalize(null)).isEmpty()`
  - `shouldReturnEmptyStringForBlank()` — `assertThat(normalizer.normalize("   ")).isEmpty()`
  - `shouldRemoveDiacritics()` — `assertThat(normalizer.normalize("café")).isEqualTo(normalizer.normalize("cafe"))`

- [X] T038 [P] [US3] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/deduplication/text/TextLocalCacheSpec.java` with `@DisplayName("Spec : TextLocalCache — Cache local de session")` and `@ExtendWith(MockitoExtension.class)`. Field: `private TextLocalCache cache`. `@BeforeEach`: `cache = new TextLocalCache()`. Write these tests:
  - `shouldReturnFalseForAbsentText()` — `assertThat(cache.contains("hello")).isFalse()`
  - `shouldReturnTrueAfterRegister()` — `cache.register("hello")` → `assertThat(cache.contains("hello")).isTrue()`
  - `shouldReturnFalseAfterClear()` (AC-3.3) — register, then clear → `assertThat(cache.contains("hello")).isFalse()`
  - `shouldNotAffectOtherEntriesWhenAdding()` — register "a"; `assertThat(cache.contains("b")).isFalse()`

- [X] T039 [P] [US3] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/deduplication/text/TextDeduplicationServiceSpec.java` with `@DisplayName("Spec : TextDeduplicationService — Déduplication de chunks texte")` and `@ExtendWith(MockitoExtension.class)`. Fields: `@Mock TextNormalizer normalizer`, `@Mock TextLocalCache localCache`, `@InjectMocks DefaultTextDeduplicationService service`. Write these tests:
  - `shouldReturnTrueForKnownChunk()` (AC-3.2) — `when(normalizer.normalize("Hello")).thenReturn("hello")`, `when(localCache.contains("hello")).thenReturn(true)` → `assertThat(service.isDuplicate("Hello")).isTrue()`
  - `shouldReturnFalseForNewChunk()` — mock returns false → `assertThat(service.isDuplicate("new")).isFalse()`
  - `shouldNotRegisterOnDuplicateCheck()` — call `isDuplicate()` → `verify(localCache, never()).register(anyString())`
  - `shouldDelegateToNormalizer()` — `verify(normalizer).normalize("raw chunk")`

**Checkpoint US-3**: `./mvnw test -Dtest="TextNormalizerSpec,TextLocalCacheSpec,TextDeduplicationServiceSpec" -pl nex-rag` — all tests PASS.

---

## Phase 6: US-4 — Antivirus Security Guard (Priority: P1)

**Goal**: ClamAV fail-secure scanner; all 3 failure modes blocked (INFECTED + unreachable + timeout).
**Independent Test**: `./mvnw test -Dtest="ClamAvResponseParserSpec,ClamAvSocketClientSpec,AntivirusGuardSpec,ClamAvHealthSchedulerSpec" -pl nex-rag`

### Production Interfaces — US-4

- [ ] T040 [P] [US4] Create interface `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/security/ClamAvSocketClient.java`:
  ```java
  public interface ClamAvSocketClient {
      ClamAvScanResult scan(InputStream content)
          throws AntivirusUnavailableException;
  }
  ```
  Import `java.io.InputStream`.

### Production Implementations — US-4

- [ ] T041 [US4] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/security/ClamAvResponseParser.java` (concrete class, no interface). Field: none. Method: `public ClamAvScanResult parse(String rawResponse)`: (1) if `rawResponse` ends with `" FOUND"` → extract threat name (text between `"stream: "` and `" FOUND"`) → return `new ClamAvScanResult(INFECTED, threatName)`. (2) if `rawResponse.trim().endsWith("OK")` → return `new ClamAvScanResult(CLEAN, null)`. (3) else → return `new ClamAvScanResult(ERROR, rawResponse)`. Never throw.

- [ ] T042 [US4] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/security/ClamAvSocketClientImpl.java` implementing `ClamAvSocketClient`. Constructor: `ClamAvSocketClientImpl(ClamAvProperties properties)`. `scan(InputStream content)`: (1) open `new Socket()`, call `socket.connect(new InetSocketAddress(properties.host(), properties.port()), properties.timeoutMs())` — wrap `ConnectException` in `AntivirusUnavailableException`. (2) `socket.setSoTimeout(properties.timeoutMs())`. (3) send `"zINSTREAM\0"` as bytes. (4) read input stream in 4KB chunks, prefix each with 4-byte BE length. (5) send 4 zero bytes. (6) read response line. (7) parse via `new ClamAvResponseParser().parse(responseLine)`. (8) wrap `SocketTimeoutException` in `AntivirusUnavailableException`. Close socket in `finally`.

- [ ] T043 [US4] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/security/AntivirusGuard.java` (concrete `@Component`). Constructor: `AntivirusGuard(ClamAvSocketClient client)`. `public void assertClean(MultipartFile file) throws VirusFoundException, AntivirusUnavailableException`: (1) call `client.scan(file.getInputStream())` — let `AntivirusUnavailableException` propagate. (2) if `result.status() == INFECTED` → throw `new VirusFoundException(result.threatName(), file.getOriginalFilename())`. (3) if `result.status() == ERROR` → throw `new AntivirusUnavailableException("Réponse ClamAV invalide: " + result.threatName())`. (4) `CLEAN` → return normally.

- [ ] T044 [US4] Create `nex-rag/src/main/java/com/exemple/nexrag/service/rag/ingestion/security/ClamAvHealthScheduler.java` (`@Component`). Constructor: `ClamAvHealthScheduler(ClamAvSocketClient client)`. Field: `private volatile boolean healthy = false`. Method `isHealthy()`: return `healthy`. Method `@Scheduled(fixedDelayString="${app.clamav.health-check-interval-ms:30000}") public void checkHealth()`: try scanning an empty stream via `client.scan(InputStream.nullInputStream())` → set `healthy = true`. On any exception → log warning with emoji `⚠️` and set `healthy = false`. Never throw.

### Test Classes — US-4

- [X] T045 [P] [US4] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/security/ClamAvResponseParserSpec.java` with `@DisplayName("Spec : ClamAvResponseParser — Interprétation réponse ClamAV")` and `@ExtendWith(MockitoExtension.class)`. Field: `private final ClamAvResponseParser parser = new ClamAvResponseParser()`. Write these tests:
  - `shouldReturnCleanForOkResponse()` — `parser.parse("stream: OK\n")` → `assertThat(result.status()).isEqualTo(CLEAN)` and `assertThat(result.threatName()).isNull()`
  - `shouldReturnInfectedForFoundResponse()` — `parser.parse("stream: Eicar-Test-Signature FOUND\n")` → `status == INFECTED` and `threatName == "Eicar-Test-Signature"`
  - `shouldReturnErrorForUnknownFormat()` — `parser.parse("ERROR: something")` → `status == ERROR`
  - `shouldNotThrowForAnyInput()` — `assertThatCode(() -> parser.parse("garbage!!!")).doesNotThrowAnyException()`

- [X] T046 [US4] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/security/ClamAvSocketClientSpec.java` with `@DisplayName("Spec : ClamAvSocketClient — Communication INSTREAM avec ClamAV")` and `@ExtendWith(MockitoExtension.class)`. Use a `ServerSocket` stub (see `research.md` Decision 5). Fields: `private ServerSocket stubServer`, `private ClamAvSocketClientImpl client`. `@BeforeEach`: open `new ServerSocket(0)`, get ephemeral port, create `ClamAvProperties("localhost", port, 2000)`, create `ClamAvSocketClientImpl(properties)`. Start virtual thread: accept one connection, write `"stream: OK\n"` bytes to output stream, close connection. `@AfterEach`: close `stubServer`. Write these tests:
  - `shouldReturnCleanForOkResponse()` — call `client.scan(InputStream.nullInputStream())` → `status == CLEAN`
  - `shouldThrowAntivirusUnavailableWhenConnectionRefused()` — create client pointing at port 1 (always refused) → `assertThatThrownBy(() -> ...).isInstanceOf(AntivirusUnavailableException.class)`
  - `shouldThrowAntivirusUnavailableOnTimeout()` — create `ServerSocket(0)` that accepts but never replies with `timeoutMs=100` → assert `AntivirusUnavailableException` within 500ms

- [X] T047 [P] [US4] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/security/AntivirusGuardSpec.java` with `@DisplayName("Spec : AntivirusGuard — Garde antivirale fail-secure")` and `@ExtendWith(MockitoExtension.class)`. Fields: `@Mock ClamAvSocketClient client`, `@InjectMocks AntivirusGuard guard`. Helper: `MockMultipartFile file = new MockMultipartFile("f","test.pdf","application/pdf","data".getBytes())`. Write these tests:
  - `shouldPassForCleanFile()` (AC-4.2) — `when(client.scan(any())).thenReturn(new ClamAvScanResult(CLEAN, null))` → `assertThatCode(() -> guard.assertClean(file)).doesNotThrowAnyException()`
  - `shouldThrowVirusFoundExceptionForInfectedFile()` (AC-4.1) — mock returns `INFECTED, "Eicar"` → `assertThatThrownBy(...).isInstanceOf(VirusFoundException.class).hasMessageContaining("Eicar")`
  - `shouldThrowAntivirusUnavailableWhenClientThrows()` (AC-4.3 connection refused) — `when(client.scan(any())).thenThrow(new AntivirusUnavailableException("down"))` → assert `AntivirusUnavailableException` propagates
  - `shouldThrowAntivirusUnavailableOnErrorStatus()` — mock returns `ERROR, "bad"` → assert `AntivirusUnavailableException`

- [X] T048 [P] [US4] Create `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/security/ClamAvHealthSchedulerSpec.java` with `@DisplayName("Spec : ClamAvHealthScheduler — Vérification périodique ClamAV")` and `@ExtendWith(MockitoExtension.class)`. Fields: `@Mock ClamAvSocketClient client`, `@InjectMocks ClamAvHealthScheduler scheduler`. Write these tests:
  - `shouldBeHealthyAfterSuccessfulCheck()` — `when(client.scan(any())).thenReturn(new ClamAvScanResult(CLEAN,null))`, call `scheduler.checkHealth()` → `assertThat(scheduler.isHealthy()).isTrue()`
  - `shouldBeUnhealthyAfterFailedCheck()` — `when(client.scan(any())).thenThrow(new AntivirusUnavailableException("down"))`, call `scheduler.checkHealth()` → `assertThat(scheduler.isHealthy()).isFalse()`
  - `shouldNotThrowOnFailedCheck()` — mock throws → `assertThatCode(() -> scheduler.checkHealth()).doesNotThrowAnyException()`

**Checkpoint US-4**: `./mvnw test -Dtest="ClamAvResponseParserSpec,AntivirusGuardSpec,ClamAvHealthSchedulerSpec" -pl nex-rag` — all tests PASS.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Coverage gate validation and final clean-up.

- [X] T049 Run full coverage gate: `./mvnw verify -pl nex-rag`. Fix any class below 80% branch coverage before merging. Report shows HTML at `nex-rag/target/site/jacoco/index.html`.
- [X] T050 [P] Confirm all Spec classes have `@ExtendWith(MockitoExtension.class)`: grep `src/test` for any `*Spec.java` missing this annotation. Add if absent.
- [X] T051 [P] Confirm all `@Test` methods have `@DisplayName` in French imperative form (`"DOIT … quand …"`): grep for `@Test` not followed by `@DisplayName`. Add any missing display names.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories (types must compile)
- **US-1 (Phase 3)**: Depends on Phase 2 (uses `FileSizeExceededException`, `InvalidFileTypeException`)
- **US-2 (Phase 4)**: Depends on Phase 2 (uses `DeduplicationStore`, `HashComputer`)
- **US-3 (Phase 5)**: Depends on Phase 2; independent of US-1 and US-2
- **US-4 (Phase 6)**: Depends on Phase 2 (uses `ClamAvScanResult`, `AntivirusUnavailableException`, `VirusFoundException`)
- **Polish (Phase 7)**: Depends on all user stories being complete

### User Story Dependencies

- **US-1** and **US-2** and **US-3** and **US-4** are all independent of each other after Phase 2 — they can be implemented in any order or in parallel by different developers.

### Within Each User Story

- Interface task [P] → can run in parallel with other interfaces
- Implementation task → depends on its interface
- Spec test class → depends on both interface and implementation (needs types to compile)
- Test MUST fail before implementation exists (red phase), then pass after (green phase)

---

## Parallel Execution Examples

### Run US-1 and US-2 in parallel (after Phase 2 complete)

```bash
# Terminal A — US-1
./mvnw test -Dtest="FileTypeDetectorSpec,FileValidatorSpec,MetadataSanitizerSpec" -pl nex-rag

# Terminal B — US-2
./mvnw test -Dtest="HashComputerSpec,DeduplicationStoreSpec,DeduplicationServiceSpec" -pl nex-rag
```

### All Phase 2 value objects in parallel (T004–T011 all write different files)

```bash
# All can be implemented simultaneously — no file conflicts
```

---

## Implementation Strategy

### MVP First (US-1 Only)

1. Complete Phase 1 (Setup) + Phase 2 (Foundational)
2. Complete Phase 3 (US-1): interfaces → implementations → Spec tests
3. **STOP and VALIDATE**: `./mvnw test -Dtest="FileTypeDetectorSpec,FileValidatorSpec,MetadataSanitizerSpec"`
4. If green → proceed to US-2

### Incremental Delivery Order (recommended for solo developer)

1. Phase 1 + Phase 2 (foundation)
2. US-4 first (AntivirusGuard) — highest security risk, simplest mocking
3. US-2 (file dedup) — pure functions, easiest to verify
4. US-1 (file validation) — requires Tika + Commons Imaging dependency check
5. US-3 (text dedup) — last, builds on normalisation patterns already understood

### Full Parallel (two developers)

- Dev A: Phase 2 + US-1 + US-2
- Dev B: Phase 2 + US-3 + US-4 (independently — different files, no conflicts)

---

## Notes

- `[P]` = writes to a different file than any other `[P]` task in the same phase; safe to run concurrently
- Each `*Spec.java` must compile before it can be run — create interfaces before test classes
- Constitution Principle I requires ALL Spec classes to use `@ExtendWith(MockitoExtension.class)`, not `@SpringBootTest`
- `ClamAvSocketClientSpec` is the only test that opens a real socket; it uses an ephemeral port and completes well under 500 ms
- If a production class already exists in the codebase, skip its creation task and go directly to the Spec task
