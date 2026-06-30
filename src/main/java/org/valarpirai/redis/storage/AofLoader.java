package org.valarpirai.redis.storage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valarpirai.redis.command.CommandExecutor;
import org.valarpirai.redis.protocol.RespDecoder;

public class AofLoader {

  private static final Logger log = LoggerFactory.getLogger(AofLoader.class);

  private AofLoader() {}

  public static void replay(String aofPath, IStorage storage) {
    File file = new File(aofPath);
    if (!file.exists()) return;

    var executor = new CommandExecutor(storage);
    int loaded = 0;
    try (var reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
      String[] tokens;
      while ((tokens = RespDecoder.decode(reader)) != null) {
        executor.executeCommand(tokens);
        loaded++;
      }
      log.info("AOF replay: loaded {} commands from {}", loaded, aofPath);
    } catch (IOException e) {
      log.error("AOF replay failed: {}", e.getMessage());
    }
  }
}
