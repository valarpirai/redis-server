package org.valarpirai.redis;

import java.util.Arrays;

public class CommandExecutor {

  private final IStorage storage;

  public CommandExecutor(IStorage storage) {
    this.storage = storage;
  }

  public String execute(String commandStr) {
    if (commandStr == null || commandStr.isBlank()) {
      return "-ERR empty command";
    }

    String[] tokens = commandStr.trim().split("\\s+");
    String command = tokens[0].toUpperCase();

    switch (command) {
      case "PING":
        return "PONG";

      case "GET":
        {
          if (tokens.length != 2) return "-ERR wrong number of arguments for 'GET'";
          String value = storage.get(tokens[1]);
          return value != null ? value : "(nil)";
        }

      case "SET":
        {
          if (tokens.length < 3) return "-ERR wrong number of arguments for 'SET'";
          String value = String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length));
          storage.set(tokens[1], value);
          return "OK";
        }

      case "DEL":
        {
          if (tokens.length != 2) return "-ERR wrong number of arguments for 'DEL'";
          return storage.delete(tokens[1]) ? "1" : "0";
        }

      case "EXISTS":
        {
          if (tokens.length != 2) return "-ERR wrong number of arguments for 'EXISTS'";
          return storage.exists(tokens[1]) ? "1" : "0";
        }

      default:
        return "-ERR unknown command '" + tokens[0] + "'";
    }
  }
}
