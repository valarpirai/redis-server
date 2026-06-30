package org.valarpirai.redis.protocol;

import org.valarpirai.redis.command.CommandResult;

public class RespEncoder {

  private RespEncoder() {}

  public static String encode(CommandResult result) {
    return switch (result) {
      case CommandResult.Ok r -> "+OK\r\n";
      case CommandResult.Pong r -> "+PONG\r\n";
      case CommandResult.Bulk r -> "$" + r.value().length() + "\r\n" + r.value() + "\r\n";
      case CommandResult.Integer r -> ":" + r.value() + "\r\n";
      case CommandResult.Error r -> r.message() + "\r\n";
      case CommandResult.Nil r -> "$-1\r\n";
    };
  }
}
