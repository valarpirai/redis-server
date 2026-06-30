package org.valarpirai.redis.command;

import java.util.Arrays;
import org.valarpirai.redis.server.ServerStats;
import org.valarpirai.redis.storage.AofWriter;
import org.valarpirai.redis.storage.EvictionPolicy;
import org.valarpirai.redis.storage.IStorage;

public class CommandExecutor {

  private final IStorage storage;
  private final AofWriter aofWriter;
  private final ServerStats stats;
  private final long maxMemoryBytes;
  private final EvictionPolicy evictionPolicy;

  public CommandExecutor(IStorage storage) {
    this(storage, null, null, 0, EvictionPolicy.NOEVICTION);
  }

  public CommandExecutor(IStorage storage, AofWriter aofWriter) {
    this(storage, aofWriter, null, 0, EvictionPolicy.NOEVICTION);
  }

  public CommandExecutor(IStorage storage, AofWriter aofWriter, ServerStats stats) {
    this(storage, aofWriter, stats, 0, EvictionPolicy.NOEVICTION);
  }

  public CommandExecutor(
      IStorage storage,
      AofWriter aofWriter,
      ServerStats stats,
      long maxMemoryBytes,
      EvictionPolicy evictionPolicy) {
    this.storage = storage;
    this.aofWriter = aofWriter;
    this.stats = stats;
    this.maxMemoryBytes = maxMemoryBytes;
    this.evictionPolicy = evictionPolicy;
  }

  public CommandResult executeCommand(String[] tokens) {
    if (tokens == null || tokens.length == 0) {
      return CommandResult.error("-ERR empty command");
    }

    if (stats != null) stats.commandProcessed();

    String command = tokens[0].toUpperCase();

    switch (command) {
      case "PING":
        return CommandResult.pong();

      case "GET":
        {
          if (tokens.length != 2)
            return CommandResult.error("-ERR wrong number of arguments for 'GET'");
          return storage.get(tokens[1]).map(CommandResult::bulk).orElseGet(CommandResult::nil);
        }

      case "SET":
        {
          if (tokens.length < 3)
            return CommandResult.error("-ERR wrong number of arguments for 'SET'");
          CommandResult oom = enforceMemoryLimit();
          if (oom != null) return oom;
          String value = String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length));
          storage.set(tokens[1], value);
          appendToAof("SET", tokens[1], value);
          return CommandResult.ok();
        }

      case "DEL":
        {
          if (tokens.length != 2)
            return CommandResult.error("-ERR wrong number of arguments for 'DEL'");
          boolean deleted = storage.delete(tokens[1]);
          if (deleted) appendToAof("DEL", tokens[1]);
          return CommandResult.integer(deleted ? 1 : 0);
        }

      case "EXISTS":
        {
          if (tokens.length != 2)
            return CommandResult.error("-ERR wrong number of arguments for 'EXISTS'");
          return CommandResult.integer(storage.exists(tokens[1]) ? 1 : 0);
        }

      case "EXPIRE":
        {
          if (tokens.length != 3)
            return CommandResult.error("-ERR wrong number of arguments for 'EXPIRE'");
          try {
            long seconds = Long.parseLong(tokens[2]);
            boolean set = storage.expire(tokens[1], seconds);
            if (set) {
              long epochMs = System.currentTimeMillis() + seconds * 1000;
              appendToAof("EXPIREAT", tokens[1], String.valueOf(epochMs));
            }
            return CommandResult.integer(set ? 1 : 0);
          } catch (NumberFormatException e) {
            return CommandResult.error("-ERR value is not an integer or out of range");
          }
        }

      case "EXPIREAT":
        {
          if (tokens.length != 3)
            return CommandResult.error("-ERR wrong number of arguments for 'EXPIREAT'");
          try {
            long epochMs = Long.parseLong(tokens[2]);
            boolean set = storage.expireAt(tokens[1], epochMs);
            return CommandResult.integer(set ? 1 : 0);
          } catch (NumberFormatException e) {
            return CommandResult.error("-ERR value is not an integer or out of range");
          }
        }

      case "TTL":
        {
          if (tokens.length != 2)
            return CommandResult.error("-ERR wrong number of arguments for 'TTL'");
          return CommandResult.integer((int) storage.ttl(tokens[1]));
        }

      case "STATS":
        return CommandResult.bulk(buildStats());

      default:
        return CommandResult.error("-ERR unknown command '" + tokens[0] + "'");
    }
  }

  /** Convenience overload for plain-text callers and tests. Returns the text value. */
  public String execute(String commandStr) {
    if (commandStr == null || commandStr.isBlank()) {
      return "-ERR empty command";
    }
    CommandResult result = executeCommand(commandStr.trim().split("\\s+"));
    return switch (result) {
      case CommandResult.Nil r -> "(nil)";
      case CommandResult.Ok r -> "OK";
      case CommandResult.Pong r -> "PONG";
      case CommandResult.Bulk r -> r.value();
      case CommandResult.Integer r -> String.valueOf(r.value());
      case CommandResult.Error r -> r.message();
    };
  }

  private CommandResult enforceMemoryLimit() {
    if (maxMemoryBytes <= 0) return null;
    while (storage.usedMemoryBytes() >= maxMemoryBytes) {
      if (evictionPolicy == EvictionPolicy.NOEVICTION) {
        return CommandResult.error("-OOM command not allowed when used memory > 'maxmemory'");
      }
      if (!storage.evict(evictionPolicy)) {
        return CommandResult.error("-OOM unable to evict keys to free memory");
      }
    }
    return null;
  }

  private void appendToAof(String... command) {
    if (aofWriter != null) aofWriter.append(command);
  }

  private String buildStats() {
    var sb = new StringBuilder();
    sb.append("# Server\r\n");
    sb.append("uptime_seconds:").append(stats != null ? stats.uptimeSeconds() : 0).append("\r\n");
    sb.append("# Clients\r\n");
    sb.append("connected_clients:")
        .append(stats != null ? stats.connectedClients() : 0)
        .append("\r\n");
    sb.append("# Stats\r\n");
    sb.append("total_commands_processed:")
        .append(stats != null ? stats.totalCommands() : 0)
        .append("\r\n");
    sb.append("# Keys\r\n");
    sb.append("total_keys:").append(storage.size()).append("\r\n");
    sb.append("# Memory\r\n");
    sb.append("used_memory_bytes:").append(storage.usedMemoryBytes()).append("\r\n");
    sb.append("max_memory_bytes:").append(maxMemoryBytes).append("\r\n");
    sb.append("eviction_policy:").append(evictionPolicy.label()).append("\r\n");
    sb.append("# Persistence\r\n");
    sb.append("aof_enabled:").append(aofWriter != null ? 1 : 0).append("\r\n");
    return sb.toString();
  }
}
