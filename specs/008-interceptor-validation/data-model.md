# Data Model: PHASE 8 — Interceptor & Validation

**Branch**: `008-interceptor-validation` | **Date**: 2026-04-04

---

## Entities

### RateLimitResult *(value object — immutable)*

Represents the outcome of a single quota-check operation.

| Field | Type | Constraints |
|-------|------|-------------|
| `allowed` | `boolean` | `true` if the request may proceed |
| `remainingTokens` | `long` | ≥ 0; meaningful only when `allowed = true`; 0 on fail-open |
| `retryAfterSeconds` | `long` | ≥ 0; meaningful only when `allowed = false` |

**Factory methods** (static):
- `RateLimitResult.allowed(long remainingTokens)` → sets `allowed=true`, `retryAfterSeconds=0`
- `RateLimitResult.blocked(long retryAfterSeconds)` → sets `allowed=false`, `remainingTokens=0`

**State invariants**:
- `allowed=true` ⟹ `retryAfterSeconds=0`
- `allowed=false` ⟹ `remainingTokens=0`
- Fail-open case: `allowed=true`, `remainingTokens=0`

---

### ClientIdentity *(resolved at request time — not persisted)*

Represents the identity used as the quota-tracking key for a given HTTP request.

| Resolution Step | Source | Condition |
|----------------|--------|-----------|
| 1 | `X-User-Id` request header | header present and non-blank |
| 2 | `userId` session attribute | session exists and attribute non-null |
| 3 | First value in `X-Forwarded-For` | header present and non-blank (leftmost IP trusted) |
| 4 | `X-Real-IP` header | header present and non-blank |
| 5 | `request.getRemoteAddr()` | fallback — always available |

---

### RateLimitQuota *(distributed state — stored in Redis)*

Tracks the rolling token-bucket state for one `(clientId, endpointCategory)` pair.

| Field | Type | Value |
|-------|------|-------|
| Redis key | `String` | `rate-limit:{clientId}:{endpointCategory}` |
| `endpointCategory` | `String` | `"upload"` \| `"batch"` \| `"delete"` \| `"search"` \| `"default"` |
| `requestsPerMinute` | `int` | upload: 10, batch: 5, delete: 20, search: 50, default: 30 |
| Window type | — | Rolling token-bucket (continuous refill); no fixed-calendar reset |
| Enforcement scope | — | Global across all service instances via shared Redis store |

**Routing rules** (URI + HTTP method → endpointCategory):

| Condition | Category |
|-----------|----------|
| URI contains `/upload/batch` | `batch` |
| URI contains `/upload` | `upload` |
| URI contains `/search` | `search` |
| `DELETE` + URI contains `/file/`, `/batch/`, or `/files/` | `delete` |
| All other | `default` |

**Bypass**: `OPTIONS` method → skip quota check entirely (CORS preflight).

---

### FileConstraint *(static rules — no persistence)*

The set of rules a document file must satisfy to be accepted by `FileValidator`.

| Rule | Value | Source constant |
|------|-------|----------------|
| Max size | 5,368,709,120 bytes (5 GB) | `FileSizeConstants.MAX_FILE_SIZE_BYTES` |
| Blocked extensions | `{exe, bat, cmd, msi, com, scr, vbs, ps1, sh}` | `FileValidator.BLOCKED_EXTENSIONS` |
| Filename required | non-null, non-blank | — |
| Content required | > 0 bytes | — |

**Batch rule**: fail-fast — validation stops at the first invalid file; remaining files are not inspected.

---

### AudioConstraint *(static rules — no persistence)*

The set of rules an audio file must satisfy to be accepted by `AudioFileValidator`.

| Rule | Value | Source constant |
|------|-------|----------------|
| Max size | 26,214,400 bytes (25 MB) | `VoiceConstants.MAX_AUDIO_SIZE_BYTES` |
| Content required | > 0 bytes | — |

---

### FileSignature *(static registry — 19 entries)*

Maps a declared file extension to its expected binary magic bytes.

| Extension | Magic bytes (hex) | Notes |
|-----------|-------------------|-------|
| `pdf` | `25 50 44 46` | `%PDF` |
| `docx`, `xlsx`, `pptx`, `zip` | `50 4B 03 04` | ZIP/OOXML container (shared) |
| `doc`, `xls`, `ppt` | `D0 CF 11 E0` | OLE2 container |
| `png` | `89 50 4E 47 0D 0A 1A 0A` | 8 bytes |
| `jpg`, `jpeg` | `FF D8 FF` | 3 bytes |
| `gif` | `47 49 46 38` | `GIF8` |
| `bmp` | `42 4D` | `BM` |
| `tiff`, `tif` | `49 49 2A 00` | Little-endian |
| `webp` | `52 49 46 46` | `RIFF` |
| `rar` | `52 61 72 21` | `Rar!` |
| `7z` | `37 7A BC AF` | — |
| `gz` | `1F 8B` | — |
| `exe`, `dll` | `4D 5A` | `MZ` |
| `sh` | `23 21` | shebang `#!` |
| `bat` | `40 65 63 68 6F` | `@echo` |

**Extensions without signatures** (validation skipped): `txt`, `csv`, `json`, `xml`, `md` and any extension not in the registry.

**DOCX/XLSX/PPTX equivalence rule** (`isExtensionMatching`): when detected type is `zip`, `docx`, `xlsx`, or `pptx`, and declared extension is also one of those four → `extensionMatches = true`.

---

### ValidationResult *(value object — record)*

Outcome of a `FileSignatureValidator.validateComplete()` call.

| Field | Type | Description |
|-------|------|-------------|
| `isValid` | `boolean` | `true` if both dangerous-extension check and signature check passed |
| `errorMessage` | `String` (nullable) | Human-readable rejection reason; `null` when valid |
| `detectedType` | `String` (nullable) | Extension detected from magic bytes; `null` if unrecognised |
| `extensionMatches` | `boolean` | `true` if detected type matches declared extension (or equivalence group) |

**Derived property**: `isSuspicious()` → `!extensionMatches`

---

## State Transition: Quota Check Flow

```
Request arrives
    │
    ▼
OPTIONS? ──yes──► allow (bypass)
    │no
    ▼
resolveUserId()
    │
    ▼
selectLimit(userId, path, method)
    │
    ▼
RateLimitService.check(userId, endpoint, config)
    │
    ├── Redis available?
    │       │no ──► RateLimitResult.allowed(0)  [fail-open]
    │       │yes
    │       ▼
    │   bucket.tryConsumeAndReturnRemaining(1)
    │       │
    │       ├── probe.isConsumed() = true ──► RateLimitResult.allowed(remainingTokens)
    │       │
    │       └── probe.isConsumed() = false ──► RateLimitResult.blocked(retryAfterSeconds)
    │
    ▼
isAllowed() = true? ──yes──► add X-RateLimit-Remaining header, return true
    │no
    ▼
set status 429
add Retry-After, X-RateLimit-Remaining=0, X-RateLimit-Reset headers
write JSON body
return false
```

---

## Test File Mapping

| Production class | Package | Spec class | Status |
|-----------------|---------|-----------|--------|
| `RateLimitInterceptor` | `service.rag.interceptor` | `RateLimitInterceptorSpec` | ⬜ to create |
| `RateLimitService` | `service.rag.interceptor` | `RateLimitServiceSpec` | ⬜ to create |
| `FileValidator` | `validation` | `FileValidatorSpec` | ✅ exists |
| `FileSignatureValidator` | `validation` | `FileSignatureValidatorSpec` | ⬜ to create |
| `AudioFileValidator` | `validation` | `AudioFileValidatorSpec` | ⬜ to create |
