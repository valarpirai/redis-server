package org.valarpirai.redis.command;

import java.util.List;

public sealed interface CommandResult
    permits CommandResult.Ok,
        CommandResult.Pong,
        CommandResult.Bulk,
        CommandResult.Integer,
        CommandResult.Error,
        CommandResult.Nil,
        CommandResult.Array {

  record Ok() implements CommandResult {}

  record Pong() implements CommandResult {}

  record Bulk(String value) implements CommandResult {}

  record Integer(long value) implements CommandResult {}

  record Error(String message) implements CommandResult {}

  record Nil() implements CommandResult {}

  record Array(List<CommandResult> elements) implements CommandResult {}

  static CommandResult ok() {
    return new Ok();
  }

  static CommandResult pong() {
    return new Pong();
  }

  static CommandResult bulk(String value) {
    return new Bulk(value);
  }

  static CommandResult integer(long value) {
    return new Integer(value);
  }

  static CommandResult error(String message) {
    return new Error(message);
  }

  static CommandResult nil() {
    return new Nil();
  }

  static CommandResult array(List<CommandResult> elements) {
    return new Array(elements);
  }
}
