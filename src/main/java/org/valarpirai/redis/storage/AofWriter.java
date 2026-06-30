package org.valarpirai.redis.storage;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AofWriter implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(AofWriter.class);

  private final BufferedWriter writer;

  public AofWriter(String filePath) throws IOException {
    this.writer =
        new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(filePath, true), StandardCharsets.UTF_8));
  }

  public synchronized void append(String[] command) {
    try {
      writer.write(encode(command));
      writer.flush();
    } catch (IOException e) {
      log.error("AOF write failed: {}", e.getMessage());
    }
  }

  private String encode(String[] command) {
    var sb = new StringBuilder();
    sb.append('*').append(command.length).append("\r\n");
    for (String arg : command) {
      sb.append('$').append(arg.length()).append("\r\n").append(arg).append("\r\n");
    }
    return sb.toString();
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }
}
