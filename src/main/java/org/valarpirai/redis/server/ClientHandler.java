package org.valarpirai.redis.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valarpirai.redis.command.CommandExecutor;
import org.valarpirai.redis.command.CommandResult;
import org.valarpirai.redis.protocol.RespDecoder;
import org.valarpirai.redis.protocol.RespEncoder;

public class ClientHandler implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

  private final Socket socket;
  private final CommandExecutor commandExecutor;

  public ClientHandler(Socket socket, CommandExecutor commandExecutor) {
    this.socket = socket;
    this.commandExecutor = commandExecutor;
  }

  @Override
  public void run() {
    try (BufferedReader in =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = socket.getOutputStream()) {

      String[] tokens;
      while ((tokens = RespDecoder.decode(in)) != null) {
        CommandResult result = commandExecutor.executeCommand(tokens);
        out.write(RespEncoder.encode(result).getBytes(StandardCharsets.UTF_8));
        out.flush();
      }
    } catch (IOException e) {
      log.error("Client handler error: {}", e.getMessage());
    } finally {
      try {
        socket.close();
        log.info("Client disconnected: {}", socket.getInetAddress());
      } catch (IOException e) {
        log.error("Error closing socket: {}", e.getMessage());
      }
    }
  }
}
