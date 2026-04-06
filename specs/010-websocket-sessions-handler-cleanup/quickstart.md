# Quickstart: PHASE 10 — WebSocket Sessions, Handler & Cleanup Tests

**Branch**: `PHASE-10-WebSocket-Sessions-Handler-Cleanup` | **Date**: 2026-04-06

---

## Prerequisites

- Java 21 installed
- Maven wrapper available (`./mvnw`) in `nex-rag/`
- No infrastructure required — all dependencies mocked; no Docker, no Redis, no database

---

## Run All Phase 10 Tests

```bash
cd nex-rag

./mvnw test -Dtest="WebSocketSessionManagerSpec,WebSocketHandlerSpec,WebSocketAssistantControllerSpec,WebSocketCleanupTaskSpec"
```

---

## Run by Sub-Group

```bash
# Session management only (US-24, US-25)
./mvnw test -Dtest="WebSocketSessionManagerSpec"

# Generic handler lifecycle and routing (US-26)
./mvnw test -Dtest="WebSocketHandlerSpec"

# RAG assistant message routing (US-27)
./mvnw test -Dtest="WebSocketAssistantControllerSpec"

# Scheduled maintenance tasks (US-28)
./mvnw test -Dtest="WebSocketCleanupTaskSpec"

# Single test method example
./mvnw test -Dtest="WebSocketSessionManagerSpec#devraitEnregistrerSessionEtLaRendreActive"
./mvnw test -Dtest="WebSocketHandlerSpec#devraitRepondrePongAUnMessagePing"
./mvnw test -Dtest="WebSocketAssistantControllerSpec#devraitCreerConversationSurMessageInit"
./mvnw test -Dtest="WebSocketCleanupTaskSpec#devraitDelegugerAuSessionManagerAvecLeSeuilConfigure"
```

---

## Run with Coverage Report

```bash
cd nex-rag

./mvnw test jacoco:report -Dtest="WebSocketSessionManagerSpec,WebSocketHandlerSpec,WebSocketAssistantControllerSpec,WebSocketCleanupTaskSpec"

# Coverage report at:
# nex-rag/target/site/jacoco/index.html
# Target: ≥ 80% line + branch coverage for com.exemple.nexrag.websocket package
```

---

## Test File Locations

```
src/test/java/com/exemple/nexrag/websocket/
├── WebSocketSessionManagerSpec.java       ✅ implemented (11 tests — US-24, US-25)
├── WebSocketHandlerSpec.java              ✅ implemented (12 tests — US-26)
├── WebSocketAssistantControllerSpec.java  ✅ implemented (12 tests — US-27)
└── WebSocketCleanupTaskSpec.java          ✅ implemented (4 tests — US-28)
```

Production sources:
```
src/main/java/com/exemple/nexrag/websocket/
├── WebSocketSessionManager.java
├── WebSocketHandler.java           (abstract)
├── WebSocketAssistantController.java
└── WebSocketCleanupTask.java

src/main/java/com/exemple/nexrag/config/
└── WebSocketProperties.java        (mocked in WebSocketCleanupTaskSpec)
```

---

## Expected Test Counts

| Spec class | Tests | User Stories covered |
|-----------|-------|---------------------|
| `WebSocketSessionManagerSpec` | 11 | US-24 (6 tests), US-25 (5 tests) |
| `WebSocketHandlerSpec` | 12 | US-26 (11 tests + 1 bonus `getSessionData` empty) |
| `WebSocketAssistantControllerSpec` | 12 | US-27 (all 12 ACs) |
| `WebSocketCleanupTaskSpec` | 4 | US-28 (all 4 ACs) |
| **Total** | **39** | **US-24 → US-28** |

---

## Constitution Compliance Checklist

Before merging Phase 10:

- [ ] Each `*Spec.java` covers exactly one production class (SRP — Principle II)
- [ ] `WebSocketHandlerSpec` uses `StubHandler` static inner class — no production subclass modified
- [ ] All `@DisplayName` values are in French imperative: `"DOIT [action] quand [condition]"` (Principle III)
- [ ] `@ExtendWith(MockitoExtension.class)` on every spec class (Principle I)
- [ ] No real WebSocket server, no real network, no Spring context started (Principle I)
- [ ] `WebSocketAssistantControllerSpec` uses real `ObjectMapper` for JSON payload assertions
- [ ] `WebSocketCleanupTaskSpec` uses `@InjectMocks`; `@Scheduled` is NOT tested (infrastructure)
- [ ] Line + branch coverage ≥ 80% for `com.exemple.nexrag.websocket` package (Principle IV)
- [ ] Every AC from US-24 → US-28 maps to at least one `@Test` method (Principle IV)
- [ ] Commit message format: `test(phase-10): add <ClassName>Spec — <brief description>` (Workflow)

---

## Commit Sequence

```bash
# One commit per Spec class (constitution: phases committed as independent units)

git add src/test/java/com/exemple/nexrag/websocket/WebSocketSessionManagerSpec.java
git commit -m "test(phase-10): add WebSocketSessionManagerSpec — cycle de vie et stats des sessions"

git add src/test/java/com/exemple/nexrag/websocket/WebSocketHandlerSpec.java
git commit -m "test(phase-10): add WebSocketHandlerSpec — cycle de vie connexions et routage messages"

git add src/test/java/com/exemple/nexrag/websocket/WebSocketAssistantControllerSpec.java
git commit -m "test(phase-10): add WebSocketAssistantControllerSpec — routage messages RAG assistant"

git add src/test/java/com/exemple/nexrag/websocket/WebSocketCleanupTaskSpec.java
git commit -m "test(phase-10): add WebSocketCleanupTaskSpec — nettoyage planifié sessions inactives"
```

---

## Troubleshooting

**Mockito strict stubbing warnings on `session.getId()`**
Use `lenient().when(session.getId()).thenReturn("session-test")` in `@BeforeEach` when the stub may not be consumed by every test method. This is the pattern used in `WebSocketSessionManagerSpec` and `WebSocketHandlerSpec`.

**`handleTextMessage` not visible**
Ensure the spec class is in `com.exemple.nexrag.websocket` (same package as the handler). The method is `protected` in `TextWebSocketHandler` — same-package access is required.

**`@InjectMocks` fails on `WebSocketCleanupTask`**
Verify both `@Mock WebSocketSessionManager` and `@Mock WebSocketProperties` are declared. `WebSocketCleanupTask` uses `@RequiredArgsConstructor` so both constructor parameters must be available as mocks.

**`broadcastToAll` test shows 0 invocations**
Call `controller.afterConnectionEstablished(session)` before `broadcastToAll` to register the session in the internal `sessions` map. Then use `clearInvocations(session)` to reset the counter so only the broadcast's `sendMessage` call is asserted.
