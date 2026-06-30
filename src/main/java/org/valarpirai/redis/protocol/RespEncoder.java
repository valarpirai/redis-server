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
      case CommandResult.Array r -> {
        StringBuilder sb = new StringBuilder("*").append(r.elements().size()).append("\r\n");
        for (CommandResult el : r.elements()) sb.append(encode(el));
        yield sb.toString();
      }
    };
  }
}
