# Architecture

## Overview

A TCP server that mimics a subset of Redis. Clients connect over a socket, send text commands, and receive text responses. The server is single-process, multi-threaded, and stateless across connections. All data lives in memory.

**Runtime:** Java 21  
**Build:** Maven  
**Port:** 6379 (default), overridable via `PORT` env var  
**Protocol:** Plain text (current) → RESP2 (Phase 2)

---

## Component Map

```
┌─────────────────────────────────────────────────────┐
│                        App                          │
│  ServerSocket + ExecutorService (fixed pool = 5)    │
└────────────────────┬────────────────────────────────┘
                     │ accept() → submit()
          ┌──────────▼──────────┐
          │    ClientHandler    │  (one per connection)
          │  reads / writes     │
          │  socket I/O         │
          └──────────┬──────────┘
                     │ execute(line)
          ┌──────────▼──────────┐
          │  CommandExecutor    │  (shared, stateless)
          │  parse + dispatch   │
          └──────────┬──────────┘
                     │ get / set
          ┌──────────▼──────────┐
          │     IStorage        │  (interface)
          └──────────┬──────────┘
                     │
          ┌──────────▼──────────┐
          │  InMemoryStorage    │
          │  ConcurrentHashMap  │
          └─────────────────────┘
```

---

## Threading Model

`App` creates a fixed thread pool of 5 platform threads via `Thread.ofPlatform().name("client-handler-", 0).factory()`. Each accepted connection is submitted as a `ClientHandler` `Runnable` to the pool.

```
main thread      pool thread 0     pool thread 1     pool thread N
    │                │                  │                  │
accept()         run()             run()              run()
    │            readLine()        readLine()         readLine()
submit() ──────► execute()         execute()          execute()
accept()         writeLine()       writeLine()        writeLine()
    │            loop              loop               loop
```

**Concurrency contract:**  
- `CommandExecutor` is shared across all pool threads. It holds no mutable state — safe.  
- `InMemoryStorage` is shared across all pool threads. `ConcurrentHashMap` handles concurrent reads and writes without external locking.  
- Each `ClientHandler` owns its own `Socket`, `BufferedReader`, and `PrintWriter`. No sharing.

**Limitation (current):** Pool is hardcoded to 5. A sixth concurrent client blocks in `accept()` until a thread frees. No backpressure or rejection policy.

---

## Request Lifecycle

```
Client                  ClientHandler           CommandExecutor        InMemoryStorage
  │                          │                        │                      │
  │── "SET foo bar\n" ──────►│                        │                      │
  │                          │── execute("SET foo bar")►                     │
  │                          │                        │── set("foo","bar") ──►│
  │                          │                        │◄─ "1" ───────────────│
  │                          │◄─ "1" ─────────────────│                      │
  │◄─ "1\n" ────────────────│                        │                      │
```

`ClientHandler.run()` loops on `readLine()`. Each line is passed as-is to `CommandExecutor.execute()`. The response is written back with `println()`. The loop exits when the client closes the connection or sends `bye`.

---

## Protocol (Current — Plain Text)

No framing. Each command is one newline-terminated line. Tokens are split on a single space. The server responds with one line per command.

| Command | Request | Response |
|---------|---------|----------|
| PING | `PING` | `PONG` |
| SET | `SET key value` | `1` |
| GET (hit) | `GET key` | `value` |
| GET (miss) | `GET key` | `null` |
| Unknown | anything else | `ERROR` |

**Known defects (plain-text protocol):**
- Multi-word values (`SET key hello world`) silently drop tokens after the third.
- Commands are case-sensitive. `get` fails; `GET` works.
- Missing args throw `ArrayIndexOutOfBoundsException` instead of returning an error.
- `null` and `ERROR` are not Redis-compliant responses.

---

## Storage Layer

`IStorage` is the boundary. `CommandExecutor` depends on the interface, not the implementation.

```java
public interface IStorage {
    String get(String key);
    String set(String key, String value);
}
```

`InMemoryStorage` backs it with a `ConcurrentHashMap<String, String>`. All values are stored as strings. There is no TTL, no type system, no persistence.

---

## Future Architecture

### Phase 1 — Command Correctness `[PENDING]`

Fix the existing command layer before adding new commands. No structural changes. All fixes are inside `CommandExecutor` and `InMemoryStorage`.

- Case-insensitive dispatch: normalise `commands[0]` with `toUpperCase()` before the `switch`.
- Arg validation: check `commands.length` before accessing indices; return `-ERR wrong number of arguments` on mismatch.
- Correct responses: `SET` returns `+OK`, missing key returns `$-1` (nil bulk), unknown command returns `-ERR unknown command 'x'`.
- Extend `IStorage`: add `delete(String key) → boolean` and `exists(String key) → boolean`.
- Implement `DEL` and `EXISTS` commands in `CommandExecutor`.

No changes to `ClientHandler`, `App`, or the socket layer.

---

### Phase 2 — RESP2 Protocol `[PENDING]`

Redis Serialization Protocol (RESP2) is required for `redis-cli` and any Redis client library to work correctly. This is a breaking change to the socket layer only — `CommandExecutor` and `IStorage` are unaffected.

**RESP2 wire format:**

```
Simple string:  +OK\r\n
Error:          -ERR message\r\n
Integer:        :1000\r\n
Bulk string:    $6\r\nfoobar\r\n
Null bulk:      $-1\r\n
Array:          *2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n
```

**What changes:**

`ClientHandler` must buffer bytes and frame RESP arrays instead of reading plain lines. A command arrives as a RESP array (`*N\r\n` followed by N bulk strings). The parsed tokens are joined into a space-separated string and passed to `CommandExecutor`, or `CommandExecutor.execute()` is refactored to accept `String[]` directly (preferred — avoids the re-split).

`CommandExecutor` response strings must be replaced with RESP-encoded values. Introduce a `RespEncoder` utility:

```
CommandExecutor
      │
      ├── parse tokens from String[]
      ├── dispatch to IStorage
      └── encode result via RespEncoder → write to PrintWriter
```

New class: `RespDecoder` — reads from `InputStream`, returns `String[]` of tokens per command.  
New class: `RespEncoder` — static methods that return RESP-encoded strings.

```
┌──────────────────────────────────────────┐
│              ClientHandler               │
│                                          │
│  RespDecoder.read(inputStream)           │
│       → String[] tokens                  │
│                                          │
│  CommandExecutor.execute(String[])       │
│       → String result                    │
│                                          │
│  RespEncoder.encode(result)              │
│       → write to outputStream            │
└──────────────────────────────────────────┘
```

---

### Phase 3 — TTL and Expiry `[PENDING]`

`EXPIRE <key> <seconds>` sets a time-to-live on a key. `TTL <key>` returns seconds remaining. Keys past their TTL are invisible to `GET`, `EXISTS`, and `DEL`.

**Storage change:**  
`InMemoryStorage` stores `ConcurrentHashMap<String, Entry>` where `Entry` holds the value and an optional expiry timestamp. `IStorage` gains two new methods:

```java
boolean expire(String key, long seconds);
long ttl(String key);          // -1 = no expiry, -2 = key missing
```

**Expiry enforcement — two strategies:**

| Strategy | How | Trade-off |
|----------|-----|-----------|
| Lazy | Check expiry on every `get` / `exists` | Simple; stale keys linger in memory |
| Active | Background thread scans and evicts | Bounded memory; adds concurrency complexity |

Start with lazy. Add active eviction only if memory becomes a concern.

**Active eviction (if added):**  
A single daemon thread (`Thread.ofPlatform().daemon(true)`) wakes every N seconds, iterates the map, removes expired entries. Use `ConcurrentHashMap.entrySet().removeIf()` — safe under concurrent reads.

---

### Phase 4 — Lifecycle and Resilience `[PENDING]`

- **Shutdown hook:** `Runtime.getRuntime().addShutdownHook(thread)` signals the server to stop accepting. Calls `executorService.shutdown()` then `executorService.awaitTermination(30, SECONDS)` to drain active handlers cleanly.
- **Idle connection timeout:** `socket.setSoTimeout(millis)` throws `SocketTimeoutException` on idle clients. `ClientHandler.run()` catches it and closes the socket, returning the thread to the pool.
- **Configurable pool size:** Read `POOL_SIZE` env var in `App.getPort()` pattern; default 5.

```
SIGTERM
   │
   ▼
ShutdownHook.run()
   │── serverSocket.close()       (stops accept() loop)
   │── executorService.shutdown() (no new tasks)
   └── awaitTermination(30s)      (drain active ClientHandlers)
```
