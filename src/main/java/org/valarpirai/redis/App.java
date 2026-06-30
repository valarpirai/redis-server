package org.valarpirai.redis;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valarpirai.redis.command.CommandExecutor;
import org.valarpirai.redis.server.ClientHandler;
import org.valarpirai.redis.server.ServerStats;
import org.valarpirai.redis.storage.AofLoader;
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
    if (config.aofEnabled()) AofLoader.replay(config.aofFile(), storage);

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
              () ->
                  ClientHandler.handle(
                      clientSocket,
                      semaphore,
                      config.maxClients(),
                      IDLE_TIMEOUT_MS,
                      commandExecutor,
                      serverStats));
        } catch (IOException e) {
          if (!serverSocket.isClosed()) log.error("Accept error: {}", e.getMessage());
        }
      }
    } finally {
      threadPool.shutdown();
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
}
