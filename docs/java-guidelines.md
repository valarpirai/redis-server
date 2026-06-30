# Java Development Guidelines

Java 21. Maven. No frameworks.

## Formatting

Code is formatted with [Spotless](https://github.com/diffplug/spotless) using Google Java Format. The pre-commit hook runs `mvn spotless:apply` automatically and re-stages any reformatted files.

```bash
mvn spotless:apply   # reformat in place
mvn spotless:check   # check without changing files (used in CI)
```

## Static Analysis and Quality Gates

Every `mvn verify` runs:

| Tool | What it checks | Fails build on |
|------|---------------|----------------|
| Maven Enforcer | Java ≥21, Maven ≥3.6 | Wrong toolchain |
| SpotBugs | Bug patterns, encoding issues, resource leaks | Medium+ severity |
| JaCoCo | Line coverage (excludes `App`) | Below 80% |
| Spotless | Google Java Format compliance | Unformatted code |

SpotBugs exclusions live in `spotbugs-exclude.xml`. Add a new exclusion only when the finding is a confirmed false positive — document the reason in the XML comment.

## Logging

Use SLF4J — never `System.out` or `System.err`.

```java
private static final Logger log = LoggerFactory.getLogger(MyClass.class);

log.info("Client connected: {}", address);
log.error("Accept error: {}", e.getMessage());
```

Logback config is in `src/main/resources/logback.xml`. Log level defaults to INFO.

## Style

Use `var` for local variables when the type is obvious from the right-hand side.

Prefer `switch` expressions with pattern matching — they are exhaustive and compiler-checked. `RespEncoder` and `CommandExecutor` demonstrate this pattern over `CommandResult`.

One class per file. Package: `org.valarpirai.redis`.

## Concurrency

`InMemoryStorage` is shared across threads. Use `ConcurrentHashMap` — never `HashMap`. Do not add `synchronized` blocks unless you have a compound operation that `ConcurrentHashMap` atomics cannot cover.

`ClientHandler` runs on the executor thread pool. Keep it short. Do not block the thread with long-running I/O outside the socket read loop.

## Error Handling

Do not swallow exceptions silently. Log via SLF4J before discarding. Do not let checked exceptions bubble out of `Runnable.run()` — catch and log them there.

## Adding Commands

1. Add the method signature to `IStorage` if it needs storage access.
2. Implement it in `InMemoryStorage`.
3. Add the `case` branch in `CommandExecutor.executeCommand(String[])`.
4. Return the appropriate `CommandResult` subtype — `ok()`, `bulk()`, `integer()`, `error()`, or `nil()`.
5. Add a new arm to the `switch` in `CommandExecutor.execute(String)` and `RespEncoder.encode()`.

Keep `CommandExecutor` stateless. All state lives in `IStorage`.

## Key Types

**`CommandResult`** — sealed interface with record subtypes (`Ok`, `Pong`, `Bulk`, `Integer`, `Error`, `Nil`). Adding a new subtype forces every `switch` in the codebase to handle it at compile time. Use the factory methods (`CommandResult.ok()`, etc.) at call sites.

**`IStorage.get()`** — returns `Optional<String>`. Never return null from storage. Use `.map()` / `.orElseGet()` at call sites.

## Testing

JUnit 5 is configured. Tests live in `src/test/java/org/valarpirai/redis/`.

```bash
mvn test                              # all tests
mvn test -Dtest=CommandExecutorTest   # one class
```

**Unit tests** — test `CommandExecutor` and `InMemoryStorage` directly. Pass `new InMemoryStorage()` — no mocks needed. Do not spin up a socket.

**Integration tests** — `IntegrationTest` starts our Java server on a random port (`new ServerSocket(0)`) in a daemon thread, opens a real `Socket`, and exchanges RESP frames. This covers the full stack: framing → decoding → execution → encoding → wire response.

## Protocol Notes

The server speaks RESP2. `RespDecoder` handles framing; `RespEncoder` handles response encoding. Keep both stateless utilities. `ClientHandler` orchestrates them but owns no protocol logic itself — it only calls `decode → executeCommand → encode → write`.

`RespDecoder` falls back to plain-text line splitting when input does not start with `*`. This keeps `nc`/telnet clients working. Do not remove this fallback.
