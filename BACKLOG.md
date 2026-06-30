# Backlog

## Protocol
- [x] Implement RESP2 protocol — `RespDecoder` / `RespEncoder` / `CommandResult`
- [x] Fix multi-word value handling — `SET key hello world` now joins all tokens

## CommandExecutor
- [x] Case-insensitive command matching
- [x] Validate argument count — returns `-ERR wrong number of arguments`
- [x] Return `(nil)` / `$-1\r\n` for missing keys
- [x] Return `OK` for successful `SET`
- [x] Return proper error response (`-ERR unknown command 'x'`)

## Commands
- [x] `DEL <key>`
- [x] `EXISTS <key>`
- [x] `EXPIRE <key> <seconds>` / `TTL <key>`
- [ ] `INCR / DECR <key>`

## Lifecycle
- [x] `Runtime.getRuntime().addShutdownHook` for clean teardown
- [x] `executorService.awaitTermination` after `shutdown()`
- [x] Idle connection timeout (`socket.setSoTimeout`)
- [x] Configurable pool size via `POOL_SIZE` env var
