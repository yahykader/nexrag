# Quickstart: Run Phase 2 Interceptor Tests

**Branch**: `011-phase2-interceptors`
**Date**: 2026-04-28

---

## Prerequisites

```bash
# From agentic-rag-ui/ directory
npm install      # ensure @ngneat/spectator ^22.1.0 is installed
```

---

## Run Phase 2 tests only

```bash
# From agentic-rag-ui/
ng test --include="**/core/interceptors/**"
```

## Run with coverage

```bash
ng test --include="**/core/interceptors/**" --code-coverage
```

Coverage report lands in `coverage/agentic-rag-ui/` — open `index.html` to verify
that `duplicate-interceptor.ts` and `rate-limit.interceptor.ts` both reach 100% branch
coverage.

## Run all tests

```bash
npm test
# or
ng test
```

---

## Spec file locations

```
agentic-rag-ui/src/app/core/interceptors/
├── duplicate-interceptor.ts          ← production file
├── duplicate-interceptor.spec.ts     ← Phase 2 spec (to be created)
├── rate-limit.interceptor.ts         ← production file
└── rate-limit.interceptor.spec.ts    ← Phase 2 spec (to be created)
```

---

## Verify a single spec in isolation

```bash
# duplicate interceptor only
ng test --include="**/duplicate-interceptor.spec.ts"

# rate-limit interceptor only
ng test --include="**/rate-limit.interceptor.spec.ts"
```

Both commands MUST pass independently (test isolation, Constitution Principle VI).

---

## Expected output (green run)

```
PASS  src/app/core/interceptors/duplicate-interceptor.spec.ts
  DuplicateInterceptor
    ✓ doit enrichir l'erreur avec isDuplicate=true quand le statut est 409
    ✓ doit utiliser "Unknown" pour filename si absent du body 409
    ✓ doit utiliser null pour batchId si absent du body 409
    ✓ doit laisser passer la réponse 200 sans transformation
    ✓ doit retransmettre l'erreur originale sans modification quand le statut est 500

PASS  src/app/core/interceptors/rate-limit.interceptor.spec.ts
  RateLimitInterceptor
    ✓ doit ajouter le header X-User-Id quand userId est présent dans localStorage
    ✓ doit ne pas ajouter le header X-User-Id quand userId est absent
    ✓ doit ne pas modifier la requête originale (immutabilité)
    ✓ doit dispatcher updateRemainingTokens avec endpoint=upload pour /api/v1/upload/...
    ✓ doit dispatcher updateRemainingTokens avec endpoint=batch pour /api/v1/upload/batch/...
    ✓ doit dispatcher updateRemainingTokens avec endpoint=search pour /api/v1/search/...
    ✓ doit dispatcher updateRemainingTokens avec endpoint=delete pour /api/v1/delete/...
    ✓ doit dispatcher updateRemainingTokens avec endpoint=default pour /api/v1/other/...
    ✓ doit ne pas dispatcher updateRemainingTokens si le header est absent
    ✓ doit dispatcher rateLimitExceeded avec le bon payload quand le statut est 429
    ✓ doit utiliser retryAfterSeconds=60 par défaut si absent du body 429
    ✓ doit retransmettre l'erreur 429 sans la swallower
    ✓ doit ne pas dispatcher rateLimitExceeded pour une réponse 200

Test Suites: 2 passed, 2 total
Tests:       13 passed, 13 total
```
