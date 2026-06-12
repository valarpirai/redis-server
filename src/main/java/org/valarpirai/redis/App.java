package org.valarpirai.redis;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {
    public static void main(String[] args) {
        var PORT = getPort();
        System.out.println("Starting Redis server on port " + PORT);

        ExecutorService executorService = Executors.newFixedThreadPool(5,
                Thread.ofPlatform().name("client-handler-", 0).factory());

        CommandExecutor commandExecutor = new CommandExecutor(new InMemoryStorage());

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Waiting for clients...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                // Handle each client in a separate thread
                executorService.submit(new ClientHandler(clientSocket, commandExecutor));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            executorService.shutdown();
        }
    }

    public static int getPort() {
        var PORT = System.getenv("PORT");
        if (null != PORT) {
            try {
                return Integer.parseInt(PORT);
            } catch (NumberFormatException e) {
                return 6379;
            }
        }

        return 6379;
    }
}
