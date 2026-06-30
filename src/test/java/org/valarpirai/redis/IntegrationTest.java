package org.valarpirai.redis;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

  private Socket client;
  private OutputStream out;
  private BufferedReader in;

  @BeforeAll
  static void startServer() throws IOException {
    serverSocket = new ServerSocket(0);
    port = serverSocket.getLocalPort();

    var storage = new InMemoryStorage();
    var executor = new CommandExecutor(storage);

    Thread serverThread =
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
    out = client.getOutputStream();
    in = new BufferedReader(new InputStreamReader(client.getInputStream()));
  }

  @AfterEach
  void disconnect() throws IOException {
    client.close();
  }

  /** Sends a RESP array command and returns the first response line. */
  private String send(String... args) throws IOException {
    var sb = new StringBuilder();
    sb.append("*").append(args.length).append("\r\n");
    for (String arg : args) {
      sb.append("$").append(arg.length()).append("\r\n").append(arg).append("\r\n");
    }
    out.write(sb.toString().getBytes());
    out.flush();
    return in.readLine();
  }

  @Test
  void ping() throws IOException {
    assertEquals("+PONG", send("PING"));
  }

  @Test
  void setReturnsOk() throws IOException {
    assertEquals("+OK", send("SET", "name", "redis"));
  }

  @Test
  void setAndGet() throws IOException {
    send("SET", "name", "redis");
    assertEquals("$5", send("GET", "name"));
    assertEquals("redis", in.readLine());
  }

  @Test
  void getMissingKeyReturnsNilBulk() throws IOException {
    assertEquals("$-1", send("GET", "nosuchkey"));
  }

  @Test
  void delExistingKey() throws IOException {
    send("SET", "city", "tokyo");
    assertEquals(":1", send("DEL", "city"));
  }

  @Test
  void existsAfterDel() throws IOException {
    send("SET", "city", "tokyo");
    assertEquals(":1", send("EXISTS", "city"));
    send("DEL", "city");
    assertEquals(":0", send("EXISTS", "city"));
  }

  @Test
  void unknownCommandReturnsError() throws IOException {
    assertTrue(send("FLUSHALL").startsWith("-ERR"));
  }

  @Test
  void caseInsensitiveCommands() throws IOException {
    assertEquals("+PONG", send("ping"));
    assertEquals("+OK", send("set", "x", "1"));
    assertEquals("$1", send("get", "x"));
    assertEquals("1", in.readLine());
  }
}
