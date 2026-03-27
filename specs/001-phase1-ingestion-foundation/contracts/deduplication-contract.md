# Contract: File & Text Deduplication

**Package**: `com.exemple.nexrag.service.rag.ingestion.deduplication`

---

## `HashComputer` Interface

```java
public interface HashComputer {
    /**
     * Calcule le hash SHA-256 (hex) du contenu binaire.
     * @param content octets à hasher (non null; tableau vide autorisé)
     * @return chaîne hex 64 caractères (jamais null)
     */
    String compute(byte[] content);
}
```

**Behavioural contract**:
- MUST be a pure function: same input → same output, always.
- `compute(new byte[0])` MUST return `"e3b0c44298fc1c149afb4c8996fb92427ae41e4649b934ca495991b7852b855"`.
- MUST NOT throw for any input.
- Two distinct byte arrays with the same content MUST produce identical results.

---

## `DeduplicationStore` Interface

```java
public interface DeduplicationStore {
    /**
     * Vérifie si un hash existe déjà dans le store Redis.
     * @param hash hash SHA-256 hex (non null)
     * @return true si le hash est déjà enregistré
     */
    boolean exists(String hash);

    /**
     * Persiste un hash dans le store Redis.
     * @param hash hash SHA-256 hex (non null)
     */
    void save(String hash);
}
```

**Behavioural contract**:
- `exists(hash)` is IDEMPOTENT — calling it N times MUST NOT write to Redis.
- After `save(hash)`, `exists(hash)` MUST return `true`.
- `save(hash)` called multiple times with the same hash MUST NOT throw (upsert semantics).
- On Redis unavailability, both methods MUST propagate a typed exception (not swallow it).

---

## `DeduplicationService` Interface

```java
public interface DeduplicationService {
    /**
     * Détermine si un fichier est un doublon (déjà ingéré).
     * @param fileContent octets bruts du fichier
     * @return true si un fichier identique a déjà été ingéré
     */
    boolean isDuplicate(byte[] fileContent);
}
```

**Behavioural contract**:
- Computes hash via `HashComputer`, queries via `DeduplicationStore.exists()`.
- MUST NOT call `DeduplicationStore.save()` — registration is the orchestrator's responsibility.
- Calling `isDuplicate()` multiple times with the same content MUST have no side effects.

---

## `TextNormalizer` Interface

```java
public interface TextNormalizer {
    /**
     * Normalise un chunk de texte pour la déduplication.
     * @param text texte brut (peut être null ou vide)
     * @return forme canonique (lowercase, trim, sans diacritiques); jamais null
     */
    String normalize(String text);
}
```

**Behavioural contract**:
- `normalize(null)` and `normalize("  ")` MUST both return `""`.
- `normalize("Hello World")` and `normalize("  hello world  ")` MUST return the same string.
- Diacritics removal: `normalize("café")` == `normalize("cafe")`.

---

## `TextLocalCache` (concrete, batch-scoped)

```java
public class TextLocalCache {
    /** @return true si le texte normalisé est déjà dans le cache */
    boolean contains(String normalizedText);

    /** Enregistre le texte normalisé dans le cache local */
    void register(String normalizedText);

    /** Vide le cache local (sans effet sur Redis) */
    void clear();
}
```

**Behavioural contract**:
- Backed by `HashSet<String>`; lifecycle is one ingestion batch.
- `clear()` resets to empty; subsequent `contains()` calls MUST return `false`.
- `clear()` MUST NOT affect any Redis-stored data.
- Thread safety: single-threaded use per batch (no concurrent access guarantee).

---

## `TextDeduplicationService` Interface

```java
public interface TextDeduplicationService {
    /**
     * Détermine si un chunk de texte est un doublon dans la session courante.
     * @param chunk texte brut du chunk
     * @return true si le chunk (normalisé) a déjà été vu dans ce batch
     */
    boolean isDuplicate(String chunk);
}
```

**Behavioural contract**:
- Normalises via `TextNormalizer` before comparing.
- Checks `TextLocalCache` first (fast path), then Redis store (slow path).
- MUST NOT register the chunk — caller decides when to register.
- MUST NOT throw if chunk is already seen; returns `true` silently.
