package org.valarpirai.redis.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.valarpirai.redis.protocol.RespDecoder;

class AofWriterTest {

  @TempDir Path tempDir;

  @Test
  void appendWritesRespEncodedCommand() throws IOException {
    Path aofFile = tempDir.resolve("test.aof");
    try (var writer = new AofWriter(aofFile.toString())) {
      writer.append(new String[] {"SET", "foo", "bar"});
    }

    String content = Files.readString(aofFile);
    assertEquals("*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n", content);
  }

  @Test
  void appendedCommandsAreReplayableByRespDecoder() throws IOException {
    Path aofFile = tempDir.resolve("test.aof");
    try (var writer = new AofWriter(aofFile.toString())) {
      writer.append(new String[] {"SET", "key", "value"});
      writer.append(new String[] {"DEL", "key"});
    }

    try (var reader =
        new BufferedReader(new FileReader(aofFile.toFile(), StandardCharsets.UTF_8))) {
      String[] first = RespDecoder.decode(reader);
      assertArrayEquals(new String[] {"SET", "key", "value"}, first);
      String[] second = RespDecoder.decode(reader);
      assertArrayEquals(new String[] {"DEL", "key"}, second);
      assertNull(RespDecoder.decode(reader));
    }
  }

  @Test
  void appendOpensInAppendMode() throws IOException {
    Path aofFile = tempDir.resolve("test.aof");
    try (var writer = new AofWriter(aofFile.toString())) {
      writer.append(new String[] {"SET", "a", "1"});
    }
    try (var writer = new AofWriter(aofFile.toString())) {
      writer.append(new String[] {"SET", "b", "2"});
    }

    try (var reader =
        new BufferedReader(new FileReader(aofFile.toFile(), StandardCharsets.UTF_8))) {
      assertArrayEquals(new String[] {"SET", "a", "1"}, RespDecoder.decode(reader));
      assertArrayEquals(new String[] {"SET", "b", "2"}, RespDecoder.decode(reader));
    }
  }

  @Test
  void appendHandlesMultiWordValue() throws IOException {
    Path aofFile = tempDir.resolve("test.aof");
    try (var writer = new AofWriter(aofFile.toString())) {
      writer.append(new String[] {"SET", "msg", "hello world"});
    }

    String content = Files.readString(aofFile);
    assertTrue(content.contains("$11\r\nhello world\r\n"));
  }
}
