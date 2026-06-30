package org.valarpirai.redis;

import java.util.Arrays;

public class CommandExecutor {

  private final IStorage storage;

  public CommandExecutor(IStorage storage) {
    this.storage = storage;
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
          return CommandResult.ok();
        }

      case "DEL":
        {
          if (tokens.length != 2)
            return CommandResult.error("-ERR wrong number of arguments for 'DEL'");
          return CommandResult.integer(storage.delete(tokens[1]) ? 1 : 0);
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
            return CommandResult.integer(storage.expire(tokens[1], seconds) ? 1 : 0);
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
    return result.kind() == CommandResult.Kind.NIL ? "(nil)" : result.value();
  }
}
