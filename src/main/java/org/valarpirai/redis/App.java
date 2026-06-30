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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valarpirai.redis.command.CommandExecutor;
import org.valarpirai.redis.protocol.RespDecoder;
import org.valarpirai.redis.server.ClientHandler;
import org.valarpirai.redis.server.ServerStats;
import org.valarpirai.redis.storage.AofWriter;
import org.valarpirai.redis.storage.ExpiryCleanerWorker;
import org.valarpirai.redis.storage.InMemoryStorage;

public class App {

  private static final Logger log = LoggerFactory.getLogger(App.class);
  private static final int IDLE_TIMEOUT_MS = 30_000;

  public static void main(String[] args) throws IOException {
    Config config = Config.fromEnv();
    config.log(log);

    var storage = new InMemoryStorage();
    if (config.aofEnabled()) replayAof(config.aofFile(), storage);

    AofWriter aofWriter =
        config.aofEnabled() ? new AofWriter(config.aofFile(), config.fsyncPolicy()) : null;
    var serverStats = new ServerStats();
    var commandExecutor =
        new CommandExecutor(
            storage, aofWriter, serverStats, config.maxMemoryBytes(), config.evictionPolicy());

    var cleaner = new ExpiryCleanerWorker(storage, config.cleanIntervalMs());
    cleaner.start();

    var semaphore = new Semaphore(config.maxClients());
    ExecutorService threadPool = Executors.newVirtualThreadPerTaskExecutor();

    try (ServerSocket serverSocket = new ServerSocket(config.port(), 1024)) {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> shutdown(serverSocket, cleaner, aofWriter, threadPool), "shutdown-hook"));

      log.info("Waiting for clients...");

      while (!serverSocket.isClosed()) {
        try {
          Socket clientSocket = serverSocket.accept();
          log.info("Client connected: {}", clientSocket.getInetAddress());
          threadPool.submit(
              () -> handleClient(clientSocket, semaphore, config, commandExecutor, serverStats));
        } catch (IOException e) {
          if (!serverSocket.isClosed()) log.error("Accept error: {}", e.getMessage());
        }
      }
    } finally {
      threadPool.shutdown();
    }
  }

  private static void handleClient(
      Socket socket,
      Semaphore semaphore,
      Config config,
      CommandExecutor executor,
      ServerStats stats) {
    if (!semaphore.tryAcquire()) {
      log.warn("Max clients ({}) reached, rejecting connection", config.maxClients());
      try {
        socket.close();
      } catch (IOException ignored) {
      }
      return;
    }
    try {
      socket.setSoTimeout(IDLE_TIMEOUT_MS);
      new ClientHandler(socket, executor, stats).run();
    } catch (IOException e) {
      log.error("Socket setup error: {}", e.getMessage());
    } finally {
      semaphore.release();
    }
  }

  private static void shutdown(
      ServerSocket serverSocket,
      ExpiryCleanerWorker cleaner,
      AofWriter aofWriter,
      ExecutorService threadPool) {
    log.info("Shutting down...");
    try {
      serverSocket.close();
    } catch (IOException e) {
      log.error("Error closing server socket: {}", e.getMessage());
    }
    cleaner.shutdown();
    if (aofWriter != null) {
      try {
        aofWriter.close();
      } catch (IOException e) {
        log.error("Error closing AOF writer: {}", e.getMessage());
      }
    }
    threadPool.shutdown();
    try {
      if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) threadPool.shutdownNow();
    } catch (InterruptedException e) {
      threadPool.shutdownNow();
      Thread.currentThread().interrupt();
    }
    log.info("Server stopped.");
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
}
