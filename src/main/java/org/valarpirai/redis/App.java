package org.valarpirai.redis;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valarpirai.redis.command.CommandExecutor;
import org.valarpirai.redis.server.ClientHandler;
import org.valarpirai.redis.storage.InMemoryStorage;

public class App {

  private static final Logger log = LoggerFactory.getLogger(App.class);
  private static final int IDLE_TIMEOUT_MS = 30_000;

  public static void main(String[] args) {
    int port = getEnvInt("PORT", 6379);
    int poolSize = getEnvInt("POOL_SIZE", 5);

    log.info("Starting Redis server on port {} (pool={})", port, poolSize);

    ExecutorService executor =
        Executors.newFixedThreadPool(
            poolSize, Thread.ofPlatform().name("client-handler-", 0).factory());

    CommandExecutor commandExecutor = new CommandExecutor(new InMemoryStorage());

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
                    executor.shutdown();
                    try {
                      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                      }
                    } catch (InterruptedException e) {
                      executor.shutdownNow();
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
          executor.submit(new ClientHandler(clientSocket, commandExecutor));
        } catch (IOException e) {
          if (!serverSocket.isClosed()) {
            log.error("Accept error: {}", e.getMessage());
          }
        }
      }
    } catch (IOException e) {
      log.error("Failed to start server: {}", e.getMessage());
    } finally {
      executor.shutdown();
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
}
