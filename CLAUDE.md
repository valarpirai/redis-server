# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run

```bash
mvn compile          # compile
mvn exec:java        # run (port 6379)
PORT=7379 mvn exec:java  # run on custom port
mvn package          # build fat jar
java -jar target/redis-server-1.0-SNAPSHOT.jar
```

Connect with `nc localhost 6379` or `redis-cli`. The server speaks plain text, not RESP — `redis-cli` will not work correctly until RESP is implemented.

## Architecture

One thread pool, one shared `CommandExecutor`, many `ClientHandler` threads.

```
App → ServerSocket → ClientHandler (per connection, pooled)
                          ↓
                   CommandExecutor (shared, stateless)
                          ↓
                     IStorage ← InMemoryStorage (ConcurrentHashMap)
```

`ClientHandler` reads lines from the socket and writes responses back. It owns the socket lifecycle. `CommandExecutor` parses the raw line and dispatches to `IStorage`. `InMemoryStorage` is the only storage implementation.

## Key Constraints

- Protocol is plain text, not RESP. Each command is one line. Responses are one line.
- `CommandExecutor.execute()` splits on a single space — multi-word values break silently.
- Commands are case-sensitive (`GET` works, `get` does not).
- `IStorage` interface only has `get` and `set`. Add new methods there before implementing in `InMemoryStorage`.
- Thread pool is fixed at 5. Port defaults to 6379, overridden by `PORT` env var.

## Docs

| File | What it covers |
|------|---------------|
| [docs/java-guidelines.md](docs/java-guidelines.md) | Style, concurrency rules, how to add commands, testing setup, protocol notes |
| [docs/design-principles.md](docs/design-principles.md) | SOLID, DRY, YAGNI, and which patterns are in use |

## What Is Not Done Yet

See `BACKLOG.md`. The main gaps: RESP protocol, case-insensitive commands, arg validation, `DEL`/`EXISTS`/`EXPIRE`/`TTL`, and shutdown hooks. There are no tests.
