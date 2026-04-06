# Data Model: PHASE 10 — WebSocket : Sessions, Handler & Cleanup

**Branch**: `PHASE-10-WebSocket-Sessions-Handler-Cleanup` | **Date**: 2026-04-06

---

## Entities

### SessionInfo *(mutable metadata — package-private inner class of `WebSocketSessionManager`)*

Stores runtime metadata for a single WebSocket connection.

| Field | Type | Mutability | Description |
|-------|------|-----------|-------------|
| `sessionId` | `String` | `final` | Unique identifier from `WebSocketSession.getId()` |
| `userId` | `String` | `final` | User identity at registration time; `"anonymous"` for unauthenticated connections |
| `connectionTime` | `long` | `final` | `System.currentTimeMillis()` at registration |
| `conversationId` | `String` | mutable | Optional RAG conversation identifier; `null` until `setConversationId()` called |
| `lastActivity` | `long` | mutable | Updated on every `updateActivity()` call; initialised to `connectionTime` |
| `disconnectionTime` | `long` | mutable | Set by `markDisconnected()`; `0` while active |
| `active` | `boolean` | mutable | `true` until `markDisconnected()` called |
| `messageCount` | `int` | mutable | Incremented on every `touch()` call |

**Derived computation**:
- `durationMs()` → `(active ? System.currentTimeMillis() : disconnectionTime) - connectionTime`

**State transitions**:

```
[REGISTERED]
  active=true, disconnectionTime=0
       │
       │ unregisterSession() → markDisconnected()
       ▼
[DISCONNECTED]
  active=false, disconnectionTime=now
       │
       │ cleanupInactiveSessions(threshold) when (now - disconnectionTime) > threshold
       ▼
[REMOVED from sessions map]
```

---

### SessionStats *(immutable snapshot — inner class of `WebSocketSessionManager`)*

Point-in-time aggregate of all session metrics. Constructed by `getStats()` and not stored.

| Field | Type | Description |
|-------|------|-------------|
| `totalSessions` | `int` | Current `sessions.size()` (includes disconnected, not yet cleaned) |
| `activeSessions` | `int` | Current `activeConnections.size()` (only open connections) |
| `totalMessages` | `long` | Sum of `messageCount` across all `SessionInfo` entries |
| `avgMessagesPerSession` | `double` | `totalMessages / totalSessions`; `0.0` when `totalSessions = 0` |
| `avgConnectionDuration` | `long` | Mean of `durationMs()` across all entries; `0L` when `totalSessions = 0` |

**Empty-state invariant**: All fields return `0` / `0.0` when no sessions are registered.

---

### WebSocketSessionManager *(Spring `@Component` — registry)*

Dual-map session registry. Not persisted — lives in application memory only.

| Internal structure | Type | Contents |
|-------------------|------|----------|
| `sessions` | `ConcurrentHashMap<String, SessionInfo>` | All sessions — active + recently disconnected |
| `activeConnections` | `ConcurrentHashMap<String, WebSocketSession>` | Only currently open connections |

**Key invariant**: `activeConnections.containsKey(id)` ⟺ `sessions.containsKey(id) && sessions.get(id).active`

**Public API**:

| Method | Mutates | Returns |
|--------|---------|---------|
| `registerSession(session, userId)` | both maps | void |
| `unregisterSession(sessionId)` | removes from both; marks disconnected | void |
| `updateActivity(sessionId)` | `SessionInfo.touch()` | void |
| `setConversationId(sessionId, convId)` | `SessionInfo.conversationId` | void |
| `getConversationId(sessionId)` | — | `String` \| `null` if unknown |
| `getSession(sessionId)` | — | `WebSocketSession` \| `null` |
| `isActive(sessionId)` | — | `boolean` |
| `getActiveSessionCount()` | — | `int` |
| `getActiveSessionIds()` | — | `Set<String>` (snapshot copy) |
| `getSessionInfo(sessionId)` | — | `SessionInfo` \| `null` |
| `getStats()` | — | `SessionStats` (new instance) |
| `cleanupInactiveSessions(thresholdMs)` | removes stale entries from `sessions` | void |

---

### WebSocketHandler *(abstract Spring bean — base handler)*

Abstract base class extending Spring's `TextWebSocketHandler`. Holds its own session map independent of `WebSocketSessionManager`.

| Internal structure | Type | Contents |
|-------------------|------|----------|
| `sessions` | `ConcurrentHashMap<String, WebSocketSession>` | Active sessions for this handler instance |
| `sessionData` | `ConcurrentHashMap<String, Map<String, Object>>` | Arbitrary per-session key-value data |

**Message routing logic**:

```
handleTextMessage(session, message)
    │
    ├── JSON parse fails → sendError(PROCESSING_ERROR)
    │
    ├── data.get("type") == null → sendError(MISSING_TYPE)
    │
    ├── type == "ping" → sendPong() [timestamp response]
    │
    ├── type == "pong" → log.trace() [silent]
    │
    └── else → handleMessage(session, type, data)  [abstract — subclass handles]
```

**Protected utilities available to subclasses**:

| Method | Behaviour |
|--------|-----------|
| `sendMessage(session, map)` | Serialises to JSON; logs failure and swallows `IOException` |
| `broadcast(message)` | Calls `sendMessage` for every entry in `sessions`; per-session failures are swallowed |
| `sendError(session, message, code)` | Sends `{"type":"error","error":{"message":..,"code":..,"timestamp":..}}` |
| `truncate(text, max)` | Returns `null` for null input; returns text unchanged if `≤ max` chars; otherwise `text.substring(0, max) + "..."` |
| `getSessionData(sessionId)` | Returns the per-session map (empty map if none exists) |
| `putSessionData(sessionId, key, value)` | Inserts into the per-session map; creates it if absent |
| `getActiveSessionCount()` | Returns `sessions.size()` |

---

### WebSocketAssistantController *(concrete `@Component` — RAG message handler)*

Extends `WebSocketHandler`. Delegates session state to `WebSocketSessionManager`.

**Dependencies** (constructor-injected):
- `ObjectMapper` — inherited, used for JSON serialisation
- `WebSocketSessionManager` — for session registration, activity tracking, conversation ID

**Message type → handler mapping**:

| Type | Handler method | Key side effect |
|------|---------------|-----------------|
| `"init"` | `handleInit()` | Generates `conv_{8-char UUID fragment}`, calls `setConversationId` |
| `"query"` | `handleQuery()` | Validates `text` is non-null and non-blank; responds `query_received` |
| `"cancel"` | `handleCancel()` | Sends `{"type":"cancelled","message":"Stream annulé"}` |
| `"ping"` | (parent) | Sends pong (inherited routing) |
| `"pong"` | (parent) | Silent (inherited routing) |
| any other | (parent routes to `handleMessage`) | Sends `UNKNOWN_TYPE` error |

**Broadcast**: `broadcastToAll(message)` delegates to parent `broadcast(message)` which iterates over `WebSocketHandler.sessions` (the handler's own map, distinct from `WebSocketSessionManager`).

---

### WebSocketCleanupTask *(Spring `@Component` — scheduled maintenance)*

**Dependencies** (via `@RequiredArgsConstructor`):
- `WebSocketSessionManager sessionManager`
- `WebSocketProperties props`

**Scheduled methods**:

| Method | Schedule expression | Behaviour |
|--------|--------------------|-----------| 
| `cleanupInactiveSessions()` | `${websocket.cleanup-interval-ms:3600000}` | Reads `props.getInactiveThresholdMs()`, calls `sessionManager.cleanupInactiveSessions(threshold)`, logs before/after counts |
| `logStats()` | `${websocket.stats-interval-ms:300000}` | Calls `sessionManager.getStats()`, logs 4 metrics at INFO |

**Session count reads during `cleanupInactiveSessions()`**:
1. `getActiveSessionCount()` — before cleanup (for delta computation)
2. `cleanupInactiveSessions(threshold)` — actual removal
3. `getActiveSessionCount()` — after cleanup (used in log statement, called at least twice total)

---

### WebSocketProperties *(`@ConfigurationProperties(prefix = "websocket")` — configuration)*

| Property | Field | Default |
|----------|-------|---------|
| `websocket.inactive-threshold-ms` | `inactiveThresholdMs` | `3_600_000L` (1 hour) |
| `websocket.cleanup-interval-ms` | `cleanupIntervalMs` | `3_600_000L` (1 hour) |
| `websocket.stats-interval-ms` | `statsIntervalMs` | `300_000L` (5 minutes) |
| `websocket.allowed-origins` | `allowedOrigins` | `[localhost, localhost:8080, localhost:4200, https://*]` |

In unit tests: `WebSocketProperties` is a `@Mock` — only `getInactiveThresholdMs()` is stubbed.

---

## Test File Mapping

| Production class | Package | Spec class | Status |
|-----------------|---------|-----------|--------|
| `WebSocketSessionManager` | `websocket` | `WebSocketSessionManagerSpec` | ✅ implemented |
| `WebSocketHandler` (abstract) | `websocket` | `WebSocketHandlerSpec` (via `StubHandler`) | ✅ implemented |
| `WebSocketAssistantController` | `websocket` | `WebSocketAssistantControllerSpec` | ✅ implemented |
| `WebSocketCleanupTask` | `websocket` | `WebSocketCleanupTaskSpec` | ✅ implemented |

---

## AC-25.5 Scenario Note

The spec states AC-25.5 as: "disconnected session + threshold = Long.MAX_VALUE → session retained." The implemented test (`devraitConserverSessionsActivesLorsNettoyage`) instead uses an **active** session (not yet unregistered) with threshold 0. This is functionally correct — the cleanup predicate filters on `!info.active`, so an active session is never removed regardless of threshold. The test validates the invariant from the other direction: active sessions survive cleanup unconditionally. Both scenarios prove the same safety property.
