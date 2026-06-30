package org.valarpirai.redis.command;

public sealed interface CommandResult
    permits CommandResult.Ok,
        CommandResult.Pong,
        CommandResult.Bulk,
        CommandResult.Integer,
        CommandResult.Error,
        CommandResult.Nil {

  record Ok() implements CommandResult {}

  record Pong() implements CommandResult {}

  record Bulk(String value) implements CommandResult {}

  record Integer(int value) implements CommandResult {}

  record Error(String message) implements CommandResult {}

  record Nil() implements CommandResult {}

  static CommandResult ok() {
    return new Ok();
  }

  static CommandResult pong() {
    return new Pong();
  }

  static CommandResult bulk(String value) {
    return new Bulk(value);
  }

  static CommandResult integer(int value) {
    return new Integer(value);
  }

  static CommandResult error(String message) {
    return new Error(message);
  }

  static CommandResult nil() {
    return new Nil();
  }
}
