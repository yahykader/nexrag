# Implementation Plan: PHASE 10 — WebSocket : Sessions, Handler & Cleanup

**Branch**: `PHASE-10-WebSocket-Sessions-Handler-Cleanup` | **Date**: 2026-04-06 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/010-websocket-sessions-handler-cleanup/spec.md`

## Summary

Add unit test coverage for the WebSocket layer of the NexRAG backend. This phase targets four production classes — `WebSocketSessionManager`, `WebSocketHandler` (abstract), `WebSocketAssistantController`, and `WebSocketCleanupTask` — grouped under `com.exemple.nexrag.websocket`. All four test classes are already implemented. This plan documents the design decisions, AC coverage, and compliance validation for the implemented tests.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: JUnit 5 (Jupiter), Mockito, AssertJ, `spring-boot-starter-test`, `spring-boot-starter-websocket` — all already present in `pom.xml`; no new dependencies required
**Storage**: none — all state is in-memory (`ConcurrentHashMap`); no Redis or database in this phase
**Testing**: JUnit 5 + Mockito + AssertJ; `@ExtendWith(MockitoExtension.class)` on all spec classes
**Target Platform**: JVM (Windows dev / Linux CI); tests run locally and in CI
**Project Type**: web-service backend (Spring Boot 3.4.2)
**Performance Goals**: each test method < 500 ms (constitution Principle I) — all tests are in-memory only
**Constraints**: no real WebSocket server, no Spring context, no network I/O, no real filesystem; French `@DisplayName` on every class and method; log assertions NOT required (research Decision 5)
**Scale/Scope**: 4 `*Spec.java` files (all pre-implemented); 39 `@Test` methods covering US-24 → US-28

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Principle | Status | Notes |
|------|-----------|--------|-------|
| Test isolation — no real infrastructure | I | ✅ PASS | All sessions in-memory; `WebSocketSession`, `WebSocketSessionManager`, `WebSocketProperties` mocked |
| Unit tests < 500 ms | I | ✅ PASS | No I/O, no network, no Spring context; real `ObjectMapper` is fast |
| One `*Spec.java` per production class | II (SRP) | ✅ PASS | 4 spec files map 1:1 to 4 production classes; `WebSocketHandler` tested via `StubHandler` |
| Constructor injection + `@InjectMocks` / `@Mock` only | II (DIP) | ✅ PASS | All production classes use constructor injection; `@InjectMocks` on `WebSocketCleanupTask` |
| `<ClassName>Spec.java` naming | III | ✅ PASS | All four files follow the convention |
| French `@DisplayName` on class + every `@Test` | III | ✅ PASS | All `@DisplayName` values in French imperative |
| Package mirrors production tree | III | ✅ PASS | `com.exemple.nexrag.websocket` for all 4 spec files |
| ≥ 80% line+branch coverage per module | IV | ✅ PASS (target) | All public/protected methods have at least one positive and one negative path |
| Every AC maps to ≥ 1 `@Test` | IV | ✅ PASS | Verified in AC Coverage Matrix below |
| Integration tests use Testcontainers only | V | N/A | Phase 10 is unit tests only |

**Post-design re-check**: All gates still pass. No complexity violations.

## Project Structure

### Documentation (this feature)

```text
specs/010-websocket-sessions-handler-cleanup/
├── plan.md              ✅ this file
├── research.md          ✅ Phase 0 output
├── data-model.md        ✅ Phase 1 output
├── quickstart.md        ✅ Phase 1 output
├── checklists/
│   └── requirements.md  ✅ all items pass
└── tasks.md             ⬜ Phase 2 output (/speckit.tasks — not yet created)
```

### Source Code

```text
nex-rag/src/
├── main/java/com/exemple/nexrag/
│   ├── websocket/
│   │   ├── WebSocketSessionManager.java       ✅ production (do not modify)
│   │   ├── WebSocketHandler.java              ✅ production (do not modify)
│   │   ├── WebSocketAssistantController.java  ✅ production (do not modify)
│   │   └── WebSocketCleanupTask.java          ✅ production (do not modify)
│   └── config/
│       └── WebSocketProperties.java           ✅ production (mocked in tests — do not modify)
│
└── test/java/com/exemple/nexrag/websocket/
    ├── WebSocketSessionManagerSpec.java       ✅ implemented (11 tests)
    ├── WebSocketHandlerSpec.java              ✅ implemented (12 tests, includes StubHandler)
    ├── WebSocketAssistantControllerSpec.java  ✅ implemented (12 tests)
    └── WebSocketCleanupTaskSpec.java          ✅ implemented (4 tests)
```

**Structure Decision**: All test classes reside in `com.exemple.nexrag.websocket` (same package as production) to access `protected` methods — specifically `handleTextMessage` on `WebSocketHandler` subclasses — without reflection or a Spring context. No production code is modified in this phase.

---

## Phase 0: Research

*Completed — see [research.md](research.md)*

**Key decisions:**

1. **Abstract class testing**: `StubHandler` static inner class inside `WebSocketHandlerSpec` — extends `WebSocketHandler`, records lifecycle-hook calls via boolean flags, exposes protected methods via public delegates. No concrete production subclass involved.

2. **Real `ObjectMapper`**: Used directly (`new ObjectMapper()`) in `WebSocketHandlerSpec` and `WebSocketAssistantControllerSpec` — JSON payload content is part of ACs; mocking would couple tests to key ordering rather than semantic correctness.

3. **Same-package test placement**: `handleTextMessage` is `protected` in `TextWebSocketHandler` — placing specs in the same package avoids reflection and Spring context startup.

4. **`@InjectMocks` on `WebSocketCleanupTask`**: `@RequiredArgsConstructor` makes it compatible; `@Scheduled` timer is not tested (infrastructure concern).

5. **Log assertions not required**: Functional behaviour (delegate calls, message payloads, session counts) is asserted; Logback `ListAppender` would add boilerplate with low regression-detection value.

6. **`broadcastToAll` testing**: Register a real session via `afterConnectionEstablished`, then `clearInvocations`, then broadcast — tests the real `sessions` map population path without reflection.

---

## Phase 1: Design & Contracts

### `WebSocketSessionManagerSpec.java`

**Location**: `src/test/java/com/exemple/nexrag/websocket/`
**Class under test**: `WebSocketSessionManager()` (no-arg constructor, `@Component`)
**Mocks**: `@Mock WebSocketSession session` (for `getId()` → `"session-1"`)
**Instantiation**: `manager = new WebSocketSessionManager()` in `@BeforeEach`

| Test method | AC covered | Scenario |
|-------------|-----------|----------|
| `devraitEnregistrerSessionEtLaRendreActive` | AC-24.1 | `registerSession` → `isActive=true`, count=1 |
| `devraitRetournerInfosSessionApresEnregistrement` | AC-24.1 (supplementary) | `getSessionInfo` returns non-null with correct `sessionId` and `userId` |
| `devraitRendreSessionInactiveApresDesenregistrement` | AC-24.2 | `unregisterSession` → `isActive=false`, count=0 |
| `devraitIncrementerCompteurMessagesAChaquUpdateActivity` | AC-24.3 | 2× `updateActivity` → `messageCount=2` |
| `devraitStockerEtRetrouverlConversationId` | AC-24.4 | `setConversationId` then `getConversationId` → matches |
| `devraitRetournerNullPourConversationIdSessionInconnue` | AC-24.5 | unknown sessionId → `null` |
| `devraitRetournerWebSocketSessionViaGetSession` | AC-24.6 | `getSession` → same instance |
| `devraitRetournerStatsZeroSansAucuneSession` | AC-25.1 | empty manager → all stats = 0 |
| `devraitCalculerStatsCorrectementAvecUneSessionActive` | AC-25.2 | 2 messages → `avgMessages=2.0`, `totalMessages=2` |
| `devraitInclureIdentifiantsActifsDansGetActiveSessionIds` | AC-25.3 | 2 sessions → both IDs in set |
| `devraitSupprimerSessionsInactivesDepassantLeSeuil` | AC-25.4 | threshold=-1 → disconnected session removed |
| `devraitConserverSessionsActivesLorsNettoyage` | AC-25.5 (variant) | threshold=0, active session → retained |

**Import pattern**: Standard AssertJ + Mockito + `@ExtendWith(MockitoExtension.class)`; `lenient().when(session.getId())` to avoid strict stubbing failures.

---

### `WebSocketHandlerSpec.java`

**Location**: `src/test/java/com/exemple/nexrag/websocket/`
**Class under test**: `WebSocketHandler` (abstract) — via `StubHandler` inner class
**Mocks**: `@Mock WebSocketSession session`
**Instantiation**: `handler = new StubHandler(new ObjectMapper())` in `@BeforeEach`
**Key pattern**: `clearInvocations(session)` after `afterConnectionEstablished` to reset invocation counters

| Test method | AC covered | Scenario |
|-------------|-----------|----------|
| `devraitEnregistrerSessionEtAppelerOnConnectionEstablished` | AC-26.1 | connect → count=1, flag=true |
| `devraitRetirerSessionEtAppelerOnConnectionClosed` | AC-26.6 | connect then close → count=0, flag=true |
| `devraitRetirerSessionEtAppelerOnTransportError` | AC-26.7 | connect then error → count=0, flag=true |
| `devraitRepondrePongAUnMessagePing` | AC-26.2 | `ping` → `TextMessage` with `type=pong` and `timestamp` |
| `devraitEnvoyerErreurSiTypeAbsentDuMessage` | AC-26.4 | no `type` field → `MISSING_TYPE` error |
| `devraitEnvoyerErreurProcessingPourJsonInvalide` | AC-26.5 | `NOT_VALID_JSON` → `PROCESSING_ERROR` error |
| `devraitEnvoyerBroadcastAToutesLesSessions` | AC-26.8 | 2 sessions → both receive `sendMessage` |
| `devraitIgnorerMessagePongSansEnvoyerReponse` | AC-26.3 | `pong` → `never()` sendMessage |
| `devraitTronquerLesChainersPlusLonguesQueLaLimite` | AC-26.9 | 100-char input, limit 50 → 53 chars ending `"..."` |
| `devraitRetournerTexteIntactSiInferieurALaLimite` | AC-26.10 | `"Court"`, limit 50 → `"Court"` |
| `devraitRetournerNullSiTexteNull` | AC-26.10 | `null` input → `null` |
| `devraitStockerEtRecupererDonneesDeSession` | AC-26.11 | `putSessionData` → `getSessionData` returns entry |
| `devraitRetournerMapVidePourSessionSansDonnees` | bonus | unknown session → empty map |

**`StubHandler` design**: Static inner class exposing `invokeBroadcast`, `invokeTruncate`, `invokeGetSessionData`, `invokePutSessionData` as thin public wrappers around the respective protected methods.

---

### `WebSocketAssistantControllerSpec.java`

**Location**: `src/test/java/com/exemple/nexrag/websocket/`
**Class under test**: `WebSocketAssistantController(ObjectMapper, WebSocketSessionManager)`
**Mocks**: `@Mock WebSocketSessionManager sessionManager`, `@Mock WebSocketSession session`
**Instantiation**: `controller = new WebSocketAssistantController(new ObjectMapper(), sessionManager)` in `@BeforeEach`
**Session ID**: `when(session.getId()).thenReturn("session-abc")` (strict stubbing — not lenient)

| Test method | AC covered | Scenario |
|-------------|-----------|----------|
| `devraitEnregistrerSessionAnonymousEtEnvoyerConnected` | AC-27.1 | connect → `registerSession(session, "anonymous")` + `type=connected` |
| `devraitDesenregistrerSessionALaDeconnexion` | AC-27.2 | close → `unregisterSession("session-abc")` |
| `devraitDesenregistrerSessionEnCasErreurTransport` | AC-27.3 | transport error → `unregisterSession("session-abc")` |
| `devraitCreerConversationSurMessageInit` | AC-27.4 | `init` → `setConversationId` with `conv_*` + `conversation_created` |
| `devraitEnvoyerQueryReceivedPourQueryValide` | AC-27.5 | `query` with text → `query_received` with text + conversationId |
| `devraitEnvoyerErreurMissingQuerySansChampText` | AC-27.6 | `query` no `text` → `MISSING_QUERY` |
| `devraitEnvoyerErreurMissingQueryPourTexteBlank` | AC-27.7 | `query` text=`"   "` → `MISSING_QUERY` |
| `devraitEnvoyerCancelledSurMessageCancel` | AC-27.8 | `cancel` → `type=cancelled`, `message="Stream annulé"` |
| `devraitEnvoyerErreurUnknownTypePourTypeInconnu` | AC-27.9 | `weirdAction` → `UNKNOWN_TYPE` |
| `devraitAppelerUpdateActivityPourChaqueMessage` | AC-27.10 | `cancel` → `verify(sessionManager).updateActivity("session-abc")` |
| `devraitUtiliserChaineVideSiAucuneConversationActive` | AC-27.11 | `getConversationId` returns null → `conversationId=""` |
| `devraitDiffuserMessageAToutesLesSessionsViaBroadcastToAll` | AC-27.12 | `afterConnectionEstablished` + `clearInvocations` + `broadcastToAll` → 1 `sendMessage` |

**JSON payload assertion pattern**:
```java
ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
verify(session).sendMessage(captor.capture());
Map<?, ?> msg = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
assertThat(msg.get("type")).isEqualTo("...");
```

**`argThat` for conversation ID**: `argThat(id -> id.startsWith("conv_"))` — validates prefix without coupling to the UUID suffix.

---

### `WebSocketCleanupTaskSpec.java`

**Location**: `src/test/java/com/exemple/nexrag/websocket/`
**Class under test**: `WebSocketCleanupTask(@RequiredArgsConstructor)`
**Mocks**: `@Mock WebSocketSessionManager sessionManager`, `@Mock WebSocketProperties props`
**Instantiation**: `@InjectMocks WebSocketCleanupTask task` — Mockito injects by constructor type-matching

| Test method | AC covered | Scenario |
|-------------|-----------|----------|
| `devraitDelegugerAuSessionManagerAvecLeSeuilConfigure` | AC-28.1 | `props.getInactiveThresholdMs()=3_600_000L` → `cleanupInactiveSessions(3_600_000L)` called |
| `devraitAppelerGetStatsDansLogStats` | AC-28.2 | `logStats()` → `getStats()` called exactly once |
| `devraitAppelerGetActiveSessionCountAvantEtApresNettoyage` | AC-28.3 | `getActiveSessionCount()` called `atLeast(2)` times |
| `devraitLireLeSeuilDepuisWebSocketProperties` | AC-28.4 | `props.getInactiveThresholdMs()=7_200_000L` → `cleanupInactiveSessions(7_200_000L)` |

**Multi-return stub**:
```java
when(sessionManager.getActiveSessionCount()).thenReturn(5, 3, 3);
// Accounts for: before-cleanup call + after-cleanup call in log statement
```

**`@Scheduled` not tested**: Spring timer infrastructure is excluded per constitution Principle I. Methods are called directly.

---

### Contracts

No external interface contracts are modified by this phase. Phase 10 is test-only; the WebSocket handlers, session manager, and cleanup task are internal components with no public REST API contract changes.

---

## Acceptance Criteria Coverage Matrix

| AC | Test class | Test method (abbreviated) |
|----|-----------|--------------------------|
| AC-24.1 | `WebSocketSessionManagerSpec` | `devraitEnregistrerSessionEtLaRendreActive` |
| AC-24.1 (supp.) | `WebSocketSessionManagerSpec` | `devraitRetournerInfosSessionApresEnregistrement` |
| AC-24.2 | `WebSocketSessionManagerSpec` | `devraitRendreSessionInactiveApresDesenregistrement` |
| AC-24.3 | `WebSocketSessionManagerSpec` | `devraitIncrementerCompteurMessagesAChaquUpdateActivity` |
| AC-24.4 | `WebSocketSessionManagerSpec` | `devraitStockerEtRetrouverlConversationId` |
| AC-24.5 | `WebSocketSessionManagerSpec` | `devraitRetournerNullPourConversationIdSessionInconnue` |
| AC-24.6 | `WebSocketSessionManagerSpec` | `devraitRetournerWebSocketSessionViaGetSession` |
| AC-25.1 | `WebSocketSessionManagerSpec` | `devraitRetournerStatsZeroSansAucuneSession` |
| AC-25.2 | `WebSocketSessionManagerSpec` | `devraitCalculerStatsCorrectementAvecUneSessionActive` |
| AC-25.3 | `WebSocketSessionManagerSpec` | `devraitInclureIdentifiantsActifsDansGetActiveSessionIds` |
| AC-25.4 | `WebSocketSessionManagerSpec` | `devraitSupprimerSessionsInactivesDepassantLeSeuil` |
| AC-25.5 (variant) | `WebSocketSessionManagerSpec` | `devraitConserverSessionsActivesLorsNettoyage` |
| AC-26.1 | `WebSocketHandlerSpec` | `devraitEnregistrerSessionEtAppelerOnConnectionEstablished` |
| AC-26.2 | `WebSocketHandlerSpec` | `devraitRepondrePongAUnMessagePing` |
| AC-26.3 | `WebSocketHandlerSpec` | `devraitIgnorerMessagePongSansEnvoyerReponse` |
| AC-26.4 | `WebSocketHandlerSpec` | `devraitEnvoyerErreurSiTypeAbsentDuMessage` |
| AC-26.5 | `WebSocketHandlerSpec` | `devraitEnvoyerErreurProcessingPourJsonInvalide` |
| AC-26.6 | `WebSocketHandlerSpec` | `devraitRetirerSessionEtAppelerOnConnectionClosed` |
| AC-26.7 | `WebSocketHandlerSpec` | `devraitRetirerSessionEtAppelerOnTransportError` |
| AC-26.8 | `WebSocketHandlerSpec` | `devraitEnvoyerBroadcastAToutesLesSessions` |
| AC-26.9 | `WebSocketHandlerSpec` | `devraitTronquerLesChainersPlusLonguesQueLaLimite` |
| AC-26.10 | `WebSocketHandlerSpec` | `devraitRetournerTexteIntactSiInferieurALaLimite` + `devraitRetournerNullSiTexteNull` |
| AC-26.11 | `WebSocketHandlerSpec` | `devraitStockerEtRecupererDonneesDeSession` |
| AC-27.1 | `WebSocketAssistantControllerSpec` | `devraitEnregistrerSessionAnonymousEtEnvoyerConnected` |
| AC-27.2 | `WebSocketAssistantControllerSpec` | `devraitDesenregistrerSessionALaDeconnexion` |
| AC-27.3 | `WebSocketAssistantControllerSpec` | `devraitDesenregistrerSessionEnCasErreurTransport` |
| AC-27.4 | `WebSocketAssistantControllerSpec` | `devraitCreerConversationSurMessageInit` |
| AC-27.5 | `WebSocketAssistantControllerSpec` | `devraitEnvoyerQueryReceivedPourQueryValide` |
| AC-27.6 | `WebSocketAssistantControllerSpec` | `devraitEnvoyerErreurMissingQuerySansChampText` |
| AC-27.7 | `WebSocketAssistantControllerSpec` | `devraitEnvoyerErreurMissingQueryPourTexteBlank` |
| AC-27.8 | `WebSocketAssistantControllerSpec` | `devraitEnvoyerCancelledSurMessageCancel` |
| AC-27.9 | `WebSocketAssistantControllerSpec` | `devraitEnvoyerErreurUnknownTypePourTypeInconnu` |
| AC-27.10 | `WebSocketAssistantControllerSpec` | `devraitAppelerUpdateActivityPourChaqueMessage` |
| AC-27.11 | `WebSocketAssistantControllerSpec` | `devraitUtiliserChaineVideSiAucuneConversationActive` |
| AC-27.12 | `WebSocketAssistantControllerSpec` | `devraitDiffuserMessageAToutesLesSessionsViaBroadcastToAll` |
| AC-28.1 | `WebSocketCleanupTaskSpec` | `devraitDelegugerAuSessionManagerAvecLeSeuilConfigure` |
| AC-28.2 | `WebSocketCleanupTaskSpec` | `devraitAppelerGetStatsDansLogStats` |
| AC-28.3 | `WebSocketCleanupTaskSpec` | `devraitAppelerGetActiveSessionCountAvantEtApresNettoyage` |
| AC-28.4 | `WebSocketCleanupTaskSpec` | `devraitLireLeSeuilDepuisWebSocketProperties` |
