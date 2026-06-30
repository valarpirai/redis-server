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
                   CommandExecutor (shared, stateless)
                     returns CommandResult
                          ↓
                    RespEncoder (encodes CommandResult → RESP2 bytes)
                          ↓
                     IStorage ← InMemoryStorage (ConcurrentHashMap<String, Entry>)
```

`ClientHandler` owns the socket lifecycle. `RespDecoder` frames incoming bytes into `String[]` tokens. `CommandExecutor.executeCommand(String[])` dispatches to `IStorage` and returns a typed `CommandResult`. `RespEncoder` writes the RESP2 response. The plain-text `execute(String)` overload exists for unit tests only.

## Key Constraints

- Protocol is RESP2. Plain-text fallback in `RespDecoder` supports `nc`/telnet clients.
- `CommandResult` carries the RESP type (SIMPLE_STRING, BULK_STRING, INTEGER, ERROR, NIL). Do not use string heuristics to infer the type — always return the right `CommandResult` factory method from `CommandExecutor`.
- `IStorage` is the only boundary between `CommandExecutor` and storage. Add new methods there first, implement in `InMemoryStorage`, then add the `case` in `CommandExecutor`.
- `InMemoryStorage` uses a private `Entry` record. TTL expiry is lazy — checked on every `get`/`exists`/`delete`.
- Port defaults to 6379 (`PORT` env var). Pool size defaults to 5 (`POOL_SIZE` env var). Both parsed via `App.getEnvInt`.
- Shutdown hook closes `ServerSocket`, calls `executor.shutdown()`, then `awaitTermination(30s)`.
- Idle connections time out after 30 s via `socket.setSoTimeout`.

## Docs

| File | What it covers |
|------|---------------|
| [docs/architecture.md](docs/architecture.md) | Deep-dive: component map, threading model, request lifecycle, RESP2 protocol, storage layer |
| [docs/java-guidelines.md](docs/java-guidelines.md) | Style, quality gates (Enforcer/SpotBugs/JaCoCo/Spotless), logging, how to add commands, key types, testing |
| [docs/design-principles.md](docs/design-principles.md) | SOLID, DRY, YAGNI, and which patterns are in use |
