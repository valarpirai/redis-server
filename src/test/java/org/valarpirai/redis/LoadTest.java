package org.valarpirai.redis;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valarpirai.redis.command.CommandExecutor;
import org.valarpirai.redis.server.ClientHandler;
import org.valarpirai.redis.storage.InMemoryStorage;

class LoadTest {

  private static final Logger log = LoggerFactory.getLogger(LoadTest.class);
  private static final int CLIENT_COUNT = 1000;
  private static final int CLIENT_TIMEOUT_MS = 5_000;

  private static ServerSocket serverSocket;
  private static int port;

  @BeforeAll
  static void startServer() throws IOException {
    serverSocket = new ServerSocket(0, 1024);
    port = serverSocket.getLocalPort();

    var executor = new CommandExecutor(new InMemoryStorage());
    var threadPool = Executors.newVirtualThreadPerTaskExecutor();

    Thread serverThread =
        new Thread(
            () -> {
              while (!serverSocket.isClosed()) {
                try {
                  Socket conn = serverSocket.accept();
                  threadPool.submit(new ClientHandler(conn, executor));
                } catch (IOException ignored) {
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

  @Test
  void thousandConcurrentPings() throws InterruptedException {
    var latch = new CountDownLatch(CLIENT_COUNT);
    var successes = new AtomicInteger();
    var failures = new AtomicInteger();
    long start = System.currentTimeMillis();

    try (var clients = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < CLIENT_COUNT; i++) {
        clients.submit(
            () -> {
              try (Socket socket = new Socket("localhost", port);
                  OutputStream out = socket.getOutputStream();
                  BufferedReader in =
                      new BufferedReader(
                          new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                socket.setSoTimeout(CLIENT_TIMEOUT_MS);
                out.write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                if ("+PONG".equals(in.readLine())) successes.incrementAndGet();
                else failures.incrementAndGet();
              } catch (IOException e) {
                failures.incrementAndGet();
              } finally {
                latch.countDown();
              }
            });
      }
      assertTrue(latch.await(30, TimeUnit.SECONDS), "Load test timed out after 30s");
    }

    long elapsedMs = System.currentTimeMillis() - start;
    log.info(
        "thousandConcurrentPings: {}/{} OK in {}ms ({} req/s)",
        successes.get(),
        CLIENT_COUNT,
        elapsedMs,
        elapsedMs > 0 ? CLIENT_COUNT * 1000 / elapsedMs : "∞");

    assertEquals(CLIENT_COUNT, successes.get(), failures.get() + " connections failed");
  }

  @Test
  void thousandConcurrentSetGet() throws InterruptedException {
    var latch = new CountDownLatch(CLIENT_COUNT);
    var successes = new AtomicInteger();
    var failures = new AtomicInteger();
    long start = System.currentTimeMillis();

    try (var clients = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < CLIENT_COUNT; i++) {
        final int id = i;
        clients.submit(
            () -> {
              try (Socket socket = new Socket("localhost", port);
                  OutputStream out = socket.getOutputStream();
                  BufferedReader in =
                      new BufferedReader(
                          new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                socket.setSoTimeout(CLIENT_TIMEOUT_MS);

                String key = "k" + id;
                String val = "v" + id;

                out.write(resp("SET", key, val));
                out.flush();
                String setResp = in.readLine();

                out.write(resp("GET", key));
                out.flush();
                in.readLine(); // bulk header $N
                String getVal = in.readLine();

                if ("+OK".equals(setResp) && val.equals(getVal)) successes.incrementAndGet();
                else failures.incrementAndGet();
              } catch (IOException e) {
                failures.incrementAndGet();
              } finally {
                latch.countDown();
              }
            });
      }
      assertTrue(latch.await(30, TimeUnit.SECONDS), "Load test timed out after 30s");
    }

    long elapsedMs = System.currentTimeMillis() - start;
    log.info(
        "thousandConcurrentSetGet: {}/{} OK in {}ms ({} req/s)",
        successes.get(),
        CLIENT_COUNT,
        elapsedMs,
        elapsedMs > 0 ? CLIENT_COUNT * 1000 / elapsedMs : "∞");

    assertEquals(CLIENT_COUNT, successes.get(), failures.get() + " SET/GET pairs failed");
  }

  private static byte[] resp(String... args) {
    var sb = new StringBuilder();
    sb.append('*').append(args.length).append("\r\n");
    for (String arg : args) {
      sb.append('$').append(arg.length()).append("\r\n").append(arg).append("\r\n");
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }
}
