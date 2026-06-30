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
3. Add the `case` branch in `CommandExecutor.execute()`.

Keep `CommandExecutor` stateless. All state lives in `IStorage`.

## Testing

No test framework is configured yet. When adding tests, use JUnit 5:

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>
```

Run all tests: `mvn test`. Run one test class: `mvn test -Dtest=CommandExecutorTest`.

Test `CommandExecutor` directly — pass a mock or fake `IStorage`. Do not spin up a socket in unit tests.

## Protocol Notes

The server currently speaks plain text. Each command is one newline-terminated line. When RESP is implemented, `ClientHandler` will need to buffer bytes and frame multi-line messages before passing to `CommandExecutor`. Keep framing logic in `ClientHandler`, keep command logic in `CommandExecutor`.
