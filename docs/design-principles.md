# Design Principles

## SOLID

**Single Responsibility.** Each class does one thing. `ClientHandler` owns the socket. `CommandExecutor` parses and dispatches. `InMemoryStorage` stores. Do not put socket logic in `CommandExecutor` or parsing logic in `ClientHandler`.

**Open/Closed.** Add new commands without modifying existing ones. New storage backends implement `IStorage` — no changes to `CommandExecutor`.

**Liskov Substitution.** Any `IStorage` implementation must honour the same contract: `get` returns `null` for missing keys, `set` always succeeds. A future `PersistentStorage` cannot silently change these semantics.

**Interface Segregation.** Keep `IStorage` narrow. If TTL commands need a separate concern (e.g. a scheduler), introduce a second interface rather than bloating `IStorage` with methods most implementations won't need.

**Dependency Inversion.** `CommandExecutor` depends on `IStorage`, not on `InMemoryStorage`. Wire the concrete type in `App.main`. Nothing else should call `new InMemoryStorage()`.

## DRY

Parse command tokens once — in `CommandExecutor`. Do not re-split or re-validate the raw string in `ClientHandler` or `InMemoryStorage`. If the same response string (e.g. `+OK\r\n`) appears in more than one `case`, extract it to a constant.

## YAGNI

Do not add persistence, clustering, pub/sub, or scripting until they are needed. Do not abstract `CommandExecutor` into a registry or plugin system before there is a demonstrated reason. Three `case` branches are simpler than a `Map<String, CommandHandler>` when there are only three commands.

## Patterns in Use

**Strategy** — `IStorage` is a strategy. Swap `InMemoryStorage` for any other implementation without touching the rest of the system.

**Template Method** — if multiple commands share a validation step (check arg count, return error), extract the shared step into a base method and let the `case` branch supply only the variant part.

**Factory** — `App.main` is the factory. It constructs the object graph (`InMemoryStorage → CommandExecutor → ClientHandler`). Keep construction there. Do not scatter `new` calls across classes.

## What to Avoid

Do not introduce a pattern to solve a problem that does not yet exist. A `CommandRegistry`, `CommandFactory`, or `AbstractCommandHandler` is premature until the command count makes a `switch` genuinely hard to follow.
