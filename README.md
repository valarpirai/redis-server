# Redis Server

A toy Redis server implementation in Java that accepts TCP client connections and handles commands.

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

The server listens on port `6379` by default. Override with the `PORT` environment variable:

```bash
PORT=7379 mvn exec:java
```

## Connect

```bash
redis-cli
# or
nc localhost 6379
```
