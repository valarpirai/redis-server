# Java Development Guidelines

Java 21. Maven. No frameworks.

## Formatting

Code is formatted with [Spotless](https://github.com/diffplug/spotless) using Google Java Format. The pre-commit hook runs `mvn spotless:apply` automatically and re-stages any reformatted files.

To format manually:

```bash
mvn spotless:apply   # reformat in place
mvn spotless:check   # check without changing files (used in CI)
```

## Style

Use `var` for local variables when the type is obvious from the right-hand side. Do not use it when the type is unclear.

```java
var map = new ConcurrentHashMap<String, String>();  // good
var result = compute();  // bad — type is hidden
```

Prefer `switch` expressions over `switch` statements. Use pattern matching where it simplifies conditionals.

One class per file. Package: `org.valarpirai.redis`.

## Concurrency

`InMemoryStorage` is shared across threads. Use `ConcurrentHashMap` — never `HashMap`. Do not add `synchronized` blocks unless you have a compound operation that `ConcurrentHashMap` atomics cannot cover.

`ClientHandler` runs on the executor thread pool. Keep it short. Do not block the thread with long-running I/O outside the socket read loop.

## Error Handling

Do not swallow exceptions silently. Log to `System.err` before discarding. Do not let checked exceptions bubble out of `Runnable.run()` — catch and log them there.

## Adding Commands

1. Add the method signature to `IStorage` if it needs storage access.
2. Implement it in `InMemoryStorage`.
3. Add the `case` branch in `CommandExecutor.executeCommand(String[])`.
4. Return the appropriate `CommandResult` factory method — do not invent string conventions.

Keep `CommandExecutor` stateless. All state lives in `IStorage`.

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
