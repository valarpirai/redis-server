package org.valarpirai.redis;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class App {

  private static final int IDLE_TIMEOUT_MS = 30_000;

  public static void main(String[] args) {
    int port = getEnvInt("PORT", 6379);
    int poolSize = getEnvInt("POOL_SIZE", 5);

    System.out.println("Starting Redis server on port " + port + " (pool=" + poolSize + ")");

    ExecutorService executor =
        Executors.newFixedThreadPool(
            poolSize, Thread.ofPlatform().name("client-handler-", 0).factory());

    CommandExecutor commandExecutor = new CommandExecutor(new InMemoryStorage());

    try (ServerSocket serverSocket = new ServerSocket(port)) {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    System.out.println("Shutting down...");
                    try {
                      serverSocket.close();
                    } catch (IOException e) {
                      System.err.println("Error closing server socket: " + e.getMessage());
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
                    System.out.println("Server stopped.");
                  },
                  "shutdown-hook"));

      System.out.println("Waiting for clients...");

      while (!serverSocket.isClosed()) {
        try {
          Socket clientSocket = serverSocket.accept();
          clientSocket.setSoTimeout(IDLE_TIMEOUT_MS);
          System.out.println("Client connected: " + clientSocket.getInetAddress());
          executor.submit(new ClientHandler(clientSocket, commandExecutor));
        } catch (IOException e) {
          if (!serverSocket.isClosed()) {
            System.err.println("Accept error: " + e.getMessage());
          }
        }
      }
    } catch (IOException e) {
      System.err.println("Failed to start server: " + e.getMessage());
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
