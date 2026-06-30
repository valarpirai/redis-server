package org.valarpirai.redis.storage;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AofWriter implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(AofWriter.class);

  private final FileOutputStream fos;
  private final BufferedWriter writer;
  private final FsyncPolicy fsyncPolicy;
  private final ScheduledExecutorService fsyncScheduler;

  public AofWriter(String filePath) throws IOException {
    this(filePath, FsyncPolicy.EVERYSEC);
  }

  public AofWriter(String filePath, FsyncPolicy fsyncPolicy) throws IOException {
    this.fsyncPolicy = fsyncPolicy;
    this.fos = new FileOutputStream(filePath, true);
    this.writer = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8));

    if (fsyncPolicy == FsyncPolicy.EVERYSEC) {
      fsyncScheduler =
          Executors.newSingleThreadScheduledExecutor(
              r -> {
                Thread t = new Thread(r, "aof-fsync");
                t.setDaemon(true);
                return t;
              });
      fsyncScheduler.scheduleAtFixedRate(this::syncToDisk, 1, 1, TimeUnit.SECONDS);
    } else {
      fsyncScheduler = null;
    }

    log.info("AOF writer started (fsync={})", fsyncPolicy.name().toLowerCase());
  }

  public synchronized void append(String[] command) {
    try {
      writer.write(encode(command));
      writer.flush();
      if (fsyncPolicy == FsyncPolicy.ALWAYS) syncToDisk();
    } catch (IOException e) {
      log.error("AOF write failed: {}", e.getMessage());
    }
  }

  private void syncToDisk() {
    try {
      fos.getFD().sync();
    } catch (IOException e) {
      log.error("AOF fsync failed: {}", e.getMessage());
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
  public synchronized void close() throws IOException {
    if (fsyncScheduler != null) fsyncScheduler.shutdown();
    writer.flush();
    syncToDisk();
    writer.close();
  }
}
