# Architecture

## Overview

A TCP server that implements a subset of Redis. Clients connect over a socket, send RESP2 commands, and receive RESP2 responses. The server is single-process, multi-threaded, and stateless across connections. All data lives in memory with optional per-key TTL.

**Runtime:** Java 21  
**Build:** Maven  
**Port:** 6379 (default), overridable via `PORT` env var  
**Pool size:** 5 (default), overridable via `POOL_SIZE` env var  
**Protocol:** RESP2  

---

## Component Map

```
┌──────────────────────────────────────────────────────────┐
│                           App                            │
│  ServerSocket + ExecutorService + ShutdownHook           │
└──────────────────────┬───────────────────────────────────┘
                       │ accept() → setSoTimeout → submit()
            ┌──────────▼──────────┐
            │    ClientHandler    │  (one per connection, pooled)
            │                     │
            │  RespDecoder        │  → String[] tokens
            │  CommandExecutor    │  → CommandResult
            │  RespEncoder        │  → RESP2 bytes → OutputStream
            └──────────┬──────────┘
                       │ executeCommand(String[])
            ┌──────────▼──────────┐
            │  CommandExecutor    │  (shared, stateless)
            │  returns CommandResult
            └──────────┬──────────┘
                       │ get / set / delete / exists / expire / ttl
            ┌──────────▼──────────┐
            │     IStorage        │  (interface)
            └──────────┬──────────┘
                       │
            ┌──────────▼──────────┐
            │  InMemoryStorage    │
            │  ConcurrentHashMap  │
            │  <String, Entry>    │
            └─────────────────────┘
```

---

## Threading Model

`App` creates a fixed thread pool of N platform threads (default 5, configurable via `POOL_SIZE`) using `Thread.ofPlatform().name("client-handler-", 0).factory()`. Each accepted connection is submitted as a `ClientHandler` `Runnable`.

```
main thread        pool thread 0        pool thread 1
    │                   │                    │
accept()            run()                run()
setSoTimeout()      RespDecoder.decode() RespDecoder.decode()
submit() ─────────► executeCommand()     executeCommand()
accept()            RespEncoder.encode() RespEncoder.encode()
    │               write bytes          write bytes
    │               loop                 loop
```

**Concurrency contract:**
- `CommandExecutor` is shared across all pool threads. It holds no mutable state — safe.
- `InMemoryStorage` is shared across all pool threads. `ConcurrentHashMap` handles concurrent reads and writes without external locking.
- Each `ClientHandler` owns its own `Socket`, `BufferedReader`, and `OutputStream`. No sharing.
- Idle clients are evicted after 30 s via `socket.setSoTimeout(30_000)`.

---

## Request Lifecycle

```
Client                  ClientHandler              CommandExecutor      InMemoryStorage
  │                          │                           │                    │
  │── RESP array ───────────►│                           │                    │
  │  *3\r\n$3\r\nSET\r\n    │── RespDecoder.decode() ──►│                   │
  │  $3\r\nfoo\r\n           │   → ["SET","foo","bar"]   │                    │
  │  $3\r\nbar\r\n           │── executeCommand(tokens) ─►                   │
  │                          │                           │── set("foo","bar")►│
  │                          │                           │◄─ void ────────────│
  │                          │◄─ CommandResult.ok() ─────│                    │
  │                          │── RespEncoder.encode() ───│                    │
  │◄─ +OK\r\n ──────────────│                           │                    │
```

---

## Protocol — RESP2

Commands arrive as RESP arrays. The server responds with typed RESP values.

**Wire format:**

```
Simple string:  +OK\r\n
Error:          -ERR message\r\n
Integer:        :1\r\n
Bulk string:    $5\r\nhello\r\n
Null bulk:      $-1\r\n
Array (in):     *3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n
```

**`RespDecoder`** reads one frame from `BufferedReader`. If the first line starts with `*`, it reads a RESP array. Otherwise it falls back to plain-text line splitting — this allows `nc`/telnet clients to work without RESP framing.

**`CommandResult`** is a typed record that carries the value and its RESP kind:

| Kind | Used for | Wire |
|------|----------|------|
| SIMPLE_STRING | PONG, OK | `+value\r\n` |
| BULK_STRING | GET hit | `$len\r\nvalue\r\n` |
| INTEGER | DEL, EXISTS, EXPIRE, TTL | `:n\r\n` |
| ERROR | bad args, unknown cmd | `-ERR msg\r\n` |
| NIL | GET miss | `$-1\r\n` |

**`RespEncoder`** takes a `CommandResult` and returns the encoded string. It never guesses the type from the value string.

**Command table:**

| Command | Args | Response kind |
|---------|------|---------------|
| PING | — | SIMPLE_STRING |
| SET key value | ≥3 tokens | SIMPLE_STRING (OK) |
| GET key | 2 tokens | BULK_STRING or NIL |
| DEL key | 2 tokens | INTEGER (1 or 0) |
| EXISTS key | 2 tokens | INTEGER (1 or 0) |
| EXPIRE key seconds | 3 tokens | INTEGER (1 or 0) |
| TTL key | 2 tokens | INTEGER (-1, -2, or seconds) |

Commands are case-insensitive. Multi-word values in SET are joined with a space.

---

## Storage Layer

`IStorage` is the boundary. `CommandExecutor` depends on the interface only.

```java
public interface IStorage {
    String get(String key);
    void set(String key, String value);
    boolean delete(String key);
    boolean exists(String key);
    boolean expire(String key, long seconds);
    long ttl(String key);   // -1 = no expiry, -2 = key missing
}
```

`InMemoryStorage` stores `ConcurrentHashMap<String, Entry>`. `Entry` is a private record holding the value and `expiresAt` (`-1` = no expiry). Expiry is **lazy**: checked on every `get`, `exists`, and `delete`. Stale entries linger in memory until next access.

Active eviction (background sweep) is not implemented. Add it if memory pressure becomes a concern — use `ConcurrentHashMap.entrySet().removeIf()` on a daemon thread.

---

## Lifecycle

```
SIGTERM
   │
   ▼
ShutdownHook.run()
   │── serverSocket.close()          (breaks accept() loop)
   │── executor.shutdown()           (no new tasks accepted)
   └── executor.awaitTermination(30s)
            │
            ├── drains active ClientHandlers
            └── executor.shutdownNow() if timeout exceeded
```

The accept loop guards `IOException` after `serverSocket.isClosed()` so shutdown does not log a spurious error.

---

## Implemented Phases

| Phase | What | Status |
|-------|------|--------|
| 1 | Command correctness: case-insensitive, arg validation, correct responses, DEL/EXISTS | Done |
| 2 | RESP2: RespDecoder, RespEncoder, CommandResult, ClientHandler rewrite | Done |
| 3 | TTL: EXPIRE/TTL commands, Entry record, lazy expiry | Done |
| 4 | Lifecycle: shutdown hook, idle timeout, configurable pool size | Done |
