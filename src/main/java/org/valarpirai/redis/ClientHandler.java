package org.valarpirai.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

// Inner class to handle individual clients
public class ClientHandler implements Runnable {
    
    private final Socket socket;
    private final CommandExecutor commandExecutor;

    public ClientHandler(Socket socket, CommandExecutor commandExecutor) {
        this.socket = socket;
        this.commandExecutor = commandExecutor;
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received from " + socket.getInetAddress() + ": " + inputLine);

                // Echo back to client
                // out.println("Server received: " + inputLine);

                // Optional: simple command handling
                if (inputLine.equalsIgnoreCase("bye")) {
                    out.println("Goodbye!");
                    break;
                }

                out.println(commandExecutor.execute(inputLine));
            }
        } catch (IOException e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            try {
                socket.close();
                System.out.println("Client disconnected: " + socket.getInetAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
