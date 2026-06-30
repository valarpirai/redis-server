# Redis Server

A toy Redis server implementation in Java 21. Speaks RESP2, compatible with `redis-cli`.

## Requirements

- Java 21
- Maven

## Run

```bash
mvn exec:java
```

Or build and run the jar:

```bash
mvn package
java -jar target/redis-server-1.0-SNAPSHOT.jar
```

Environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `6379` | TCP port to listen on |
| `POOL_SIZE` | `5` | Client handler thread pool size |

```bash
PORT=7379 POOL_SIZE=10 mvn exec:java
```

## Test

```bash
mvn test                                  # all tests
mvn test -Dtest=CommandExecutorTest       # one class
mvn test -Dtest=IntegrationTest           # integration only
```

## Connect

```bash
redis-cli
# or
nc localhost 6379
```

## Supported Commands

| Command | Description |
|---------|-------------|
| `PING` | Returns PONG |
| `SET key value` | Store a value |
| `GET key` | Retrieve a value |
| `DEL key` | Delete a key |
| `EXISTS key` | Check if a key exists |
| `EXPIRE key seconds` | Set TTL on a key |
| `TTL key` | Get remaining TTL (-1 = no expiry, -2 = missing) |
