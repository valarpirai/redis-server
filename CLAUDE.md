# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run

```bash
mvn compile              # compile
mvn test                 # run all tests
mvn test -Dtest=CommandExecutorTest   # run one test class
mvn exec:java            # run server (port 6379)
PORT=7379 mvn exec:java  # run on custom port
POOL_SIZE=10 mvn exec:java  # custom thread pool size
AOF_FILE=redis.aof mvn exec:java  # enable AOF persistence
MAX_MEMORY_MB=64 mvn exec:java    # cap memory at 64 MB (default 0 = unlimited)
CLEAN_INTERVAL_MS=500 mvn exec:java  # expiry cleaner interval (default 10000)
mvn package              # build fat jar
java -jar target/redis-server-1.0-SNAPSHOT.jar
```

Connect with `redis-cli` or `nc localhost 6379`. The server speaks RESP2.

## Architecture

One thread pool, one shared `CommandExecutor`, many `ClientHandler` threads.

```
App → ServerSocket → ClientHandler (per connection, pooled)
                          ↓
                    RespDecoder (parses RESP array frames)
                          ↓
                   CommandExecutor (shared) ──► AofWriter (append-only file)
                     returns CommandResult
                          ↓
                    RespEncoder (encodes CommandResult → RESP2 bytes)
                          ↓
                     IStorage ← InMemoryStorage (ConcurrentHashMap<String, Entry>)
                          ↑
                   ExpiryCleanerWorker (daemon thread, periodic scan)
```

`ClientHandler` owns the socket lifecycle. `RespDecoder` frames incoming bytes into `String[]` tokens. `CommandExecutor.executeCommand(String[])` dispatches to `IStorage` and returns a typed `CommandResult`. `RespEncoder` writes the RESP2 response. After each mutating command `CommandExecutor` appends a RESP line to `AofWriter` (null = disabled). `ExpiryCleanerWorker` calls `storage.cleanExpired()` on a schedule. On startup, `App` replays the AOF file through a no-writer executor before opening the server socket. The plain-text `execute(String)` overload exists for unit tests only.

## Key Constraints

- Protocol is RESP2. Plain-text fallback in `RespDecoder` supports `nc`/telnet clients.
- `CommandResult` carries the RESP type (SIMPLE_STRING, BULK_STRING, INTEGER, ERROR, NIL). Do not use string heuristics to infer the type — always return the right `CommandResult` factory method from `CommandExecutor`.
- `IStorage` is the only boundary between `CommandExecutor` and storage. Add new methods there first, implement in `InMemoryStorage`, then add the `case` in `CommandExecutor`.
- `InMemoryStorage` uses a private `Entry` record. TTL expiry is lazy — checked on every `get`/`exists`/`delete`. `cleanExpired()` does an active sweep (called by the cleaner worker).
- `EXPIRE` is always logged to AOF as `EXPIREAT <absoluteEpochMs>` so replayed TTLs are correct regardless of when replay happens.
- Port defaults to 6379 (`PORT` env var). Pool size defaults to 5 (`POOL_SIZE` env var). AOF file path via `AOF_FILE` (empty = disabled). Cleaner interval via `CLEAN_INTERVAL_MS` (default 1000). All parsed via `App.getEnvInt`/`getEnvLong`.
- Shutdown hook closes `ServerSocket`, closes `AofWriter`, shuts down `ExpiryCleanerWorker`, calls `executor.shutdown()`, then `awaitTermination(30s)`.
- Idle connections time out after 30 s via `socket.setSoTimeout`.

## Docs

| File | What it covers |
|------|---------------|
| [docs/architecture.md](docs/architecture.md) | Deep-dive: component map, threading model, request lifecycle, RESP2 protocol, storage layer |
| [docs/java-guidelines.md](docs/java-guidelines.md) | Style, quality gates (Enforcer/SpotBugs/JaCoCo/Spotless), logging, how to add commands, key types, testing |
| [docs/design-principles.md](docs/design-principles.md) | SOLID, DRY, YAGNI, and which patterns are in use |
