# Research: PHASE 10 — WebSocket : Sessions, Handler & Cleanup

**Branch**: `PHASE-10-WebSocket-Sessions-Handler-Cleanup` | **Date**: 2026-04-06

---

## Decision 1: Testing an Abstract Class (`WebSocketHandler`) Without a Running Server

**Decision**: Define a package-private `StubHandler` static inner class inside `WebSocketHandlerSpec` that extends `WebSocketHandler`, implements all four abstract methods as flag-setters, and exposes the protected methods (`broadcast`, `truncate`, `getSessionData`, `putSessionData`) through thin public delegates.

**Rationale**: `WebSocketHandler` is abstract — it cannot be instantiated directly. The test class resides in the same package (`com.exemple.nexrag.websocket`), which grants access to `protected` methods without `@SpringBootTest` or reflection hacks. `StubHandler` captures lifecycle-hook calls via boolean flags (`connectionEstablishedCalled`, `connectionClosedCalled`, `transportErrorCalled`) and the last dispatched message type (`lastMessageType`), giving full observability over the abstract layer. No concrete implementation code is exercised by the stub — only the base-class logic is under test.

**Pattern**:
```java
static class StubHandler extends WebSocketHandler {
    boolean connectionEstablishedCalled;
    boolean connectionClosedCalled;
    boolean transportErrorCalled;
    String  lastMessageType;
    StubHandler(ObjectMapper om) { super(om); }
    @Override protected void onConnectionEstablished(WebSocketSession s) { connectionEstablishedCalled = true; }
    @Override protected void handleMessage(WebSocketSession s, String type, Map<String,Object> d) { lastMessageType = type; }
    @Override protected void onConnectionClosed(WebSocketSession s, CloseStatus st)  { connectionClosedCalled = true; }
    @Override protected void onTransportError(WebSocketSession s, Throwable e) { transportErrorCalled = true; }
    void invokeBroadcast(Map<String,Object> msg) { broadcast(msg); }
    String invokeTruncate(String text, int max) { return truncate(text, max); }
    Map<String,Object> invokeGetSessionData(String id) { return getSessionData(id); }
    void invokePutSessionData(String id, String key, Object val) { putSessionData(id, key, val); }
}
```

**Alternatives considered**:
- `@SpringBootTest` + real WebSocket server: starts a full application context; 3–10 s startup; violates Principle I (unit tests must be fast and infrastructure-free).
- Mockito `spy()` on concrete subclass: requires a non-abstract concrete class to spy on; adds a production dependency.
- Reflection to call `protected` methods: fragile, breaks on JVM security managers, not readable.

---

## Decision 2: Real `ObjectMapper` vs. Mocked `ObjectMapper`

**Decision**: Use a real `new ObjectMapper()` in `WebSocketHandlerSpec` and `WebSocketAssistantControllerSpec`. Do **not** mock it.

**Rationale**: Both handler classes serialise outgoing messages with `objectMapper.writeValueAsString(map)` and the tests parse the captured `TextMessage.getPayload()` back to a `Map` to assert individual fields (`type`, `sessionId`, `timestamp`, `conversationId`, etc.). Mocking `ObjectMapper` would require stubbing every possible `writeValueAsString` call, making tests brittle and obscuring what was actually sent. Using a real `ObjectMapper` provides a true end-to-end serialisation roundtrip within the unit test boundary — no network, no process boundary.

**Contrast with Phase 8**: `RateLimitInterceptorSpec` mocked `ObjectMapper` because the test scope was HTTP status codes and headers, not JSON shape. In Phase 10 the JSON payload content is part of the acceptance criteria, so a real mapper is correct.

**Alternatives considered**:
- Mock `ObjectMapper` + `ArgumentCaptor<TextMessage>` with string matching: brittle; tests implementation order of JSON keys rather than semantic correctness.
- `@MockBean ObjectMapper` in a Spring slice: adds Spring context startup overhead; unnecessary.

---

## Decision 3: Same-Package Test Access to `handleTextMessage`

**Decision**: Place all four `*Spec.java` files in `src/test/java/com/exemple/nexrag/websocket/` — the same package as the production classes — so that `handleTextMessage(WebSocketSession, TextMessage)` (which is `protected` in `TextWebSocketHandler`) can be called directly from the test without reflection or a Spring context.

**Rationale**: `WebSocketAssistantController` overrides `handleTextMessage` from `TextWebSocketHandler`. It is `protected`, not `public`. Calling it directly from `WebSocketAssistantControllerSpec` requires the test to be in the same package. This is the standard Java white-box testing pattern for protected methods. It enables `controller.handleTextMessage(session, new TextMessage("{...}"))` directly, which is far simpler and faster than setting up a mock `WebSocketServer` or a Spring integration context.

**Alternatives considered**:
- `@WebSocketTest` or equivalent Spring slice: no official Spring WebSocket test slice exists; would require `@SpringBootTest(webEnvironment = RANDOM_PORT)` + real WebSocket client; violates Principle I.
- Reflection `method.setAccessible(true)`: fragile, verbose, suppresses IDE navigation, fails under module-based JVM security.

---

## Decision 4: `@InjectMocks` for `WebSocketCleanupTask`

**Decision**: Use `@InjectMocks WebSocketCleanupTask` with `@Mock WebSocketSessionManager` and `@Mock WebSocketProperties`. Call `cleanupInactiveSessions()` and `logStats()` directly — do not test the `@Scheduled` timer mechanism.

**Rationale**: `WebSocketCleanupTask` uses `@RequiredArgsConstructor` for constructor injection, which `@InjectMocks` resolves automatically by type-matching the two mock fields. The `@Scheduled` annotation is a Spring infrastructure concern — its correctness depends on Spring's scheduler, not on the business logic inside the annotated method. Constitution Principle I says unit tests must not rely on infrastructure. Direct invocation of the scheduled methods tests their internal logic (delegation to `sessionManager`, reading `props`, counting sessions) in isolation.

**Multi-return stub pattern** for `getActiveSessionCount()`:
```java
when(sessionManager.getActiveSessionCount()).thenReturn(5, 3, 3);
// 1st call: before cleanup (5), 2nd: int() after cleanup (3), 3rd: in log statement (3)
```

**Alternatives considered**:
- `@SpringBootTest` + `@SpyBean WebSocketCleanupTask`: validates the scheduler fires; adds application context startup; belongs in Phase 9 (integration).
- `Awaitility` + real scheduler timer: non-deterministic; flaky in CI environments with CPU contention.

---

## Decision 5: Log Assertion Strategy

**Decision**: Do **not** assert on log output in Phase 10 unit tests. Verify observable behaviour (session counts, mock invocations, sent `TextMessage` payloads, return values) only.

**Rationale**: FR-031 and FR-032 (lifecycle event logging at INFO/WARN) specify the required logging levels — but the production code already implements these (`log.info` on established/closed, `log.error` on transport error in `WebSocketHandler`). Asserting log output via Logback `ListAppender` adds ~15 lines of boilerplate per class with low regression-detection value: the test would fail if someone renames the logger or changes phrasing, but would not catch functional regressions. The constitution (Principle IV) targets branch and line coverage, not log output. If log-level enforcement is later required, a dedicated observability test phase should be added.

**What IS asserted instead of log output**:
- `verify(sessionManager).*` for delegation correctness
- `ArgumentCaptor<TextMessage>` + `objectMapper.readValue(...)` for message payload shape
- `assertThat(handler.getActiveSessionCount())` for session count transitions
- `assertThat(handler.connectionEstablishedCalled)` and sibling flags for lifecycle hook invocation

**Alternatives considered**:
- Logback `ListAppender` injection: feasible, ~10–15 lines boilerplate per class, catches accidental log-level changes.
- SLF4J test module: adds test dependency not currently in the project BOM.

---

## Decision 6: `broadcastToAll` Testing in `WebSocketAssistantControllerSpec`

**Decision**: Register a real session via `controller.afterConnectionEstablished(session)`, then call `controller.broadcastToAll(message)`, and verify `session.sendMessage(any(TextMessage.class))` was invoked. Do **not** mock the internal `sessions` map of `WebSocketHandler`.

**Rationale**: `WebSocketAssistantController.broadcastToAll()` delegates to the parent's `broadcast()`, which iterates over `WebSocketHandler.sessions` (a `ConcurrentHashMap` field). This map is populated by `afterConnectionEstablished`. Calling `afterConnectionEstablished` in the test setup populates the map exactly as production code would. Mocking the map would require reflection or exposing it, violating encapsulation. The test uses `clearInvocations(session)` after setup to reset the send-message call count so the broadcast assertion is clean.

**Pattern**:
```java
controller.afterConnectionEstablished(session);  // populates sessions map
clearInvocations(session);                        // reset invocation count from "connected" message

controller.broadcastToAll(Map.of("type", "notification"));

verify(session).sendMessage(any(TextMessage.class));
```

**Alternatives considered**:
- Reflection to add directly to `sessions` field: fragile; breaks on field rename or visibility change.
- Provide a second session via `mock()` + `when(session2.getId()).thenReturn("s2")` + `afterConnectionEstablished(session2)`: correct pattern for multi-session broadcast (see `WebSocketHandlerSpec` broadcast test).
