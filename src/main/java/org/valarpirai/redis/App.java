package org.valarpirai.redis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valarpirai.redis.command.CommandExecutor;
import org.valarpirai.redis.protocol.RespDecoder;
import org.valarpirai.redis.server.ClientHandler;
import org.valarpirai.redis.storage.AofWriter;
import org.valarpirai.redis.storage.ExpiryCleanerWorker;
import org.valarpirai.redis.storage.InMemoryStorage;

public class App {

  private static final Logger log = LoggerFactory.getLogger(App.class);
  private static final int IDLE_TIMEOUT_MS = 30_000;

  public static void main(String[] args) throws IOException {
    int port = getEnvInt("PORT", 6379);
    int poolSize = getEnvInt("POOL_SIZE", 5);
    long cleanIntervalMs = getEnvLong("CLEAN_INTERVAL_MS", 1000);
    String aofPath = System.getenv("AOF_FILE");

    log.info("Starting Redis server on port {} (pool={})", port, poolSize);

    var storage = new InMemoryStorage();

    if (aofPath != null && !aofPath.isBlank()) {
      replayAof(aofPath, storage);
    }

    AofWriter aofWriter = null;
    if (aofPath != null && !aofPath.isBlank()) {
      aofWriter = new AofWriter(aofPath);
    }

    var commandExecutor = new CommandExecutor(storage, aofWriter);

    var cleaner = new ExpiryCleanerWorker(storage, cleanIntervalMs);
    cleaner.start();

    ExecutorService threadPool =
        Executors.newFixedThreadPool(
            poolSize, Thread.ofPlatform().name("client-handler-", 0).factory());

    final AofWriter writerRef = aofWriter;
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    log.info("Shutting down...");
                    try {
                      serverSocket.close();
                    } catch (IOException e) {
                      log.error("Error closing server socket: {}", e.getMessage());
                    }
                    cleaner.shutdown();
                    if (writerRef != null) {
                      try {
                        writerRef.close();
                      } catch (IOException e) {
                        log.error("Error closing AOF writer: {}", e.getMessage());
                      }
                    }
                    threadPool.shutdown();
                    try {
                      if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                        threadPool.shutdownNow();
                      }
                    } catch (InterruptedException e) {
                      threadPool.shutdownNow();
                      Thread.currentThread().interrupt();
                    }
                    log.info("Server stopped.");
                  },
                  "shutdown-hook"));

      log.info("Waiting for clients...");

      while (!serverSocket.isClosed()) {
        try {
          Socket clientSocket = serverSocket.accept();
          clientSocket.setSoTimeout(IDLE_TIMEOUT_MS);
          log.info("Client connected: {}", clientSocket.getInetAddress());
          threadPool.submit(new ClientHandler(clientSocket, commandExecutor));
        } catch (IOException e) {
          if (!serverSocket.isClosed()) {
            log.error("Accept error: {}", e.getMessage());
          }
        }
      }
    } finally {
      threadPool.shutdown();
    }
  }

  private static void replayAof(String aofPath, InMemoryStorage storage) {
    File file = new File(aofPath);
    if (!file.exists()) return;

    var replayExecutor = new CommandExecutor(storage);
    int loaded = 0;
    try (var reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
      String[] tokens;
      while ((tokens = RespDecoder.decode(reader)) != null) {
        replayExecutor.executeCommand(tokens);
        loaded++;
      }
      log.info("AOF replay: loaded {} commands from {}", loaded, aofPath);
    } catch (IOException e) {
      log.error("AOF replay failed: {}", e.getMessage());
    }
  }

  static int getEnvInt(String name, int defaultValue) {
    String value = System.getenv(name);
    if (value == null) return defaultValue;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  static long getEnvLong(String name, long defaultValue) {
    String value = System.getenv(name);
    if (value == null) return defaultValue;
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
