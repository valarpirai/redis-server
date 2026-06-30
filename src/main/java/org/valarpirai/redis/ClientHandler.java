package org.valarpirai.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {

  private final Socket socket;
  private final CommandExecutor commandExecutor;

  public ClientHandler(Socket socket, CommandExecutor commandExecutor) {
    this.socket = socket;
    this.commandExecutor = commandExecutor;
  }

  @Override
  public void run() {
    try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        OutputStream out = socket.getOutputStream()) {

      String[] tokens;
      while ((tokens = RespDecoder.decode(in)) != null) {
        CommandResult result = commandExecutor.executeCommand(tokens);
        out.write(RespEncoder.encode(result).getBytes());
        out.flush();
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
