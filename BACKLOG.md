# Backlog

## Protocol
- [ ] Implement RESP protocol — required for `redis-cli` compatibility (currently plain text only)
- [ ] Fix multi-word value handling — `SET key hello world` silently drops tokens after the third

## CommandExecutor
- [ ] Case-insensitive command matching (`get` / `GET` / `Get` should all work)
- [ ] Validate argument count — `GET` / `SET` with missing args throws `ArrayIndexOutOfBoundsException`
- [ ] Return `(nil)` instead of `null` for missing keys
- [ ] Return `OK` instead of `"1"` for successful `SET`
- [ ] Return a proper error response (e.g. `-ERR unknown command`) instead of `"ERROR"`

## Commands
- [ ] `DEL <key>`
- [ ] `EXISTS <key>`
- [ ] `INCR / DECR <key>`
- [ ] `EXPIRE <key> <seconds>` / `TTL <key>`

## Lifecycle
- [ ] Add `Runtime.getRuntime().addShutdownHook` for clean teardown
- [ ] Call `executorService.awaitTermination` after `shutdown()` to drain active handlers
- [ ] Add idle connection timeout to prevent stalled clients holding pool threads
