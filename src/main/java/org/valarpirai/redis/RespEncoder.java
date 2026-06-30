package org.valarpirai.redis;

public class RespEncoder {

  private RespEncoder() {}

  public static String encode(CommandResult result) {
    return switch (result.kind()) {
      case SIMPLE_STRING -> "+" + result.value() + "\r\n";
      case BULK_STRING -> "$" + result.value().length() + "\r\n" + result.value() + "\r\n";
      case INTEGER -> ":" + result.value() + "\r\n";
      case ERROR -> result.value() + "\r\n";
      case NIL -> "$-1\r\n";
    };
  }
}
