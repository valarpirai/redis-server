package org.valarpirai.redis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RespEncoderTest {

  @Test
  void encodesPong() {
    assertEquals("+PONG\r\n", RespEncoder.encode(CommandResult.pong()));
  }

  @Test
  void encodesOk() {
    assertEquals("+OK\r\n", RespEncoder.encode(CommandResult.ok()));
  }

  @Test
  void encodesNil() {
    assertEquals("$-1\r\n", RespEncoder.encode(CommandResult.nil()));
  }

  @Test
  void encodesError() {
    assertEquals(
        "-ERR unknown command 'FOO'\r\n",
        RespEncoder.encode(CommandResult.error("-ERR unknown command 'FOO'")));
  }

  @Test
  void encodesInteger() {
    assertEquals(":1\r\n", RespEncoder.encode(CommandResult.integer(1)));
    assertEquals(":0\r\n", RespEncoder.encode(CommandResult.integer(0)));
  }

  @Test
  void encodesBulkString() {
    assertEquals("$5\r\nhello\r\n", RespEncoder.encode(CommandResult.bulk("hello")));
  }

  @Test
  void encodesBulkStringWithSpaces() {
    assertEquals("$11\r\nhello world\r\n", RespEncoder.encode(CommandResult.bulk("hello world")));
  }

  @Test
  void encodesNumericStringAsBulk() {
    assertEquals("$1\r\n1\r\n", RespEncoder.encode(CommandResult.bulk("1")));
  }
}
