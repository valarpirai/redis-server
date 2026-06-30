package org.valarpirai.redis.command;

import java.util.Arrays;
import org.valarpirai.redis.storage.AofWriter;
import org.valarpirai.redis.storage.IStorage;

public class CommandExecutor {

  private final IStorage storage;
  private final AofWriter aofWriter;

  public CommandExecutor(IStorage storage) {
    this(storage, null);
  }

  public CommandExecutor(IStorage storage, AofWriter aofWriter) {
    this.storage = storage;
    this.aofWriter = aofWriter;
  }

  public CommandResult executeCommand(String[] tokens) {
    if (tokens == null || tokens.length == 0) {
      return CommandResult.error("-ERR empty command");
    }

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

  private void appendToAof(String... command) {
    if (aofWriter != null) aofWriter.append(command);
  }
}
