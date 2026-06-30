package org.valarpirai.redis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.valarpirai.redis.storage.EvictionPolicy;
import org.valarpirai.redis.storage.FsyncPolicy;

class AppTest {

  @Test
  void configFromEnvUsesDefaults() {
    Config config = Config.fromEnv();
    assertEquals(6379, config.port());
    assertEquals(1000, config.maxClients());
    assertEquals(10_000, config.cleanIntervalMs());
    assertEquals(0, config.maxMemoryMb());
    assertEquals(EvictionPolicy.NOEVICTION, config.evictionPolicy());
    assertEquals(FsyncPolicy.EVERYSEC, config.fsyncPolicy());
    assertFalse(config.aofEnabled());
  }

  @Test
  void maxMemoryBytesConvertsFromMb() {
    Config config =
        new Config(6379, 100, 1000, 64, EvictionPolicy.NOEVICTION, null, FsyncPolicy.EVERYSEC);
    assertEquals(64L * 1024 * 1024, config.maxMemoryBytes());
  }

  @Test
  void aofEnabledWhenFilePathSet() {
    Config config =
        new Config(6379, 100, 1000, 0, EvictionPolicy.NOEVICTION, "redis.aof", FsyncPolicy.ALWAYS);
    assertTrue(config.aofEnabled());
  }

  @Test
  void aofDisabledWhenFilePathEmpty() {
    Config config =
        new Config(6379, 100, 1000, 0, EvictionPolicy.NOEVICTION, "", FsyncPolicy.EVERYSEC);
    assertFalse(config.aofEnabled());
  }
}
