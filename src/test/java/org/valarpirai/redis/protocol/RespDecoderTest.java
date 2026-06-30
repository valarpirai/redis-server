package org.valarpirai.redis.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class RespDecoderTest {

  private BufferedReader reader(String input) {
    return new BufferedReader(new StringReader(input));
  }

  @Test
  void decodesRespArray() throws IOException {
    String resp = "*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n";
    String[] tokens = RespDecoder.decode(reader(resp));
    assertArrayEquals(new String[] {"GET", "foo"}, tokens);
  }

  @Test
  void decodesRespArrayThreeArgs() throws IOException {
    String resp = "*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n";
    String[] tokens = RespDecoder.decode(reader(resp));
    assertArrayEquals(new String[] {"SET", "foo", "bar"}, tokens);
  }

  @Test
  void decodesPlainTextFallback() throws IOException {
    String[] tokens = RespDecoder.decode(reader("SET foo bar\r\n"));
    assertArrayEquals(new String[] {"SET", "foo", "bar"}, tokens);
  }

  @Test
  void returnsNullOnEof() throws IOException {
    assertNull(RespDecoder.decode(reader("")));
  }
}
