package org.valarpirai.redis.server;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ServerStats {

  private final long startTimeMs = System.currentTimeMillis();
  private final AtomicLong totalCommands = new AtomicLong();
  private final AtomicInteger connectedClients = new AtomicInteger();

  public void commandProcessed() {
    totalCommands.incrementAndGet();
  }

  public void clientConnected() {
    connectedClients.incrementAndGet();
  }

  public void clientDisconnected() {
    connectedClients.decrementAndGet();
  }

  public long uptimeSeconds() {
    return (System.currentTimeMillis() - startTimeMs) / 1000;
  }

  public long totalCommands() {
    return totalCommands.get();
  }

  public int connectedClients() {
    return connectedClients.get();
  }
}
