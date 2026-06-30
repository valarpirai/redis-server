package org.valarpirai.redis;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntegrationTest {

  private static ServerSocket serverSocket;
  private static int port;
  private static Thread serverThread;

  private Socket client;
  private PrintWriter out;
  private BufferedReader in;

  @BeforeAll
  static void startServer() throws IOException {
    serverSocket = new ServerSocket(0);
    port = serverSocket.getLocalPort();

    var storage = new InMemoryStorage();
    var executor = new CommandExecutor(storage);

    serverThread =
        new Thread(
            () -> {
              while (!serverSocket.isClosed()) {
                try {
                  Socket conn = serverSocket.accept();
                  new Thread(new ClientHandler(conn, executor)).start();
                } catch (IOException e) {
                  // server stopped
                }
              }
            });
    serverThread.setDaemon(true);
    serverThread.start();
  }

  @AfterAll
  static void stopServer() throws IOException {
    serverSocket.close();
  }

  @BeforeEach
  void connect() throws IOException {
    client = new Socket("localhost", port);
    out = new PrintWriter(client.getOutputStream(), true);
    in = new BufferedReader(new InputStreamReader(client.getInputStream()));
  }

  @AfterEach
  void disconnect() throws IOException {
    client.close();
  }

  private String send(String command) throws IOException {
    out.println(command);
    return in.readLine();
  }

  @Test
  void ping() throws IOException {
    assertEquals("PONG", send("PING"));
  }

  @Test
  void setAndGet() throws IOException {
    assertEquals("OK", send("SET name redis"));
    assertEquals("redis", send("GET name"));
  }

  @Test
  void getMissingKeyReturnsNil() throws IOException {
    assertEquals("(nil)", send("GET nosuchkey"));
  }

  @Test
  void delAndExists() throws IOException {
    send("SET city tokyo");
    assertEquals("1", send("EXISTS city"));
    assertEquals("1", send("DEL city"));
    assertEquals("0", send("EXISTS city"));
  }

  @Test
  void unknownCommandReturnsError() throws IOException {
    assertTrue(send("FLUSHALL").startsWith("-ERR"));
  }

  @Test
  void caseInsensitiveCommands() throws IOException {
    assertEquals("PONG", send("ping"));
    assertEquals("OK", send("set x 1"));
    assertEquals("1", send("get x"));
  }
}
