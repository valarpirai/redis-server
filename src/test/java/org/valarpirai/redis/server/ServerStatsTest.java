package org.valarpirai.redis.server;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ServerStatsTest {

  @Test
  void initialStateIsZero() {
    var stats = new ServerStats();
    assertEquals(0, stats.totalCommands());
    assertEquals(0, stats.connectedClients());
  }

  @Test
  void commandProcessedIncrementsCounter() {
    var stats = new ServerStats();
    stats.commandProcessed();
    stats.commandProcessed();
    assertEquals(2, stats.totalCommands());
  }

  @Test
  void clientConnectedAndDisconnected() {
    var stats = new ServerStats();
    stats.clientConnected();
    stats.clientConnected();
    assertEquals(2, stats.connectedClients());
    stats.clientDisconnected();
    assertEquals(1, stats.connectedClients());
  }

  @Test
  void uptimeSecondsIsNonNegative() throws InterruptedException {
    var stats = new ServerStats();
    Thread.sleep(10);
    assertTrue(stats.uptimeSeconds() >= 0);
  }
}
