package org.valarpirai.redis;

public record CommandResult(String value, Kind kind) {

  public enum Kind {
    SIMPLE_STRING,
    BULK_STRING,
    INTEGER,
    ERROR,
    NIL
  }

  public static CommandResult ok() {
    return new CommandResult("OK", Kind.SIMPLE_STRING);
  }

  public static CommandResult pong() {
    return new CommandResult("PONG", Kind.SIMPLE_STRING);
  }

  public static CommandResult nil() {
    return new CommandResult(null, Kind.NIL);
  }

  public static CommandResult integer(int n) {
    return new CommandResult(String.valueOf(n), Kind.INTEGER);
  }

  public static CommandResult bulk(String value) {
    return new CommandResult(value, Kind.BULK_STRING);
  }

  public static CommandResult error(String message) {
    return new CommandResult(message, Kind.ERROR);
  }
}
