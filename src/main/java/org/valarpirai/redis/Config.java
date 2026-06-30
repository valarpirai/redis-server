package org.valarpirai.redis;

import org.slf4j.Logger;
import org.valarpirai.redis.storage.EvictionPolicy;
import org.valarpirai.redis.storage.FsyncPolicy;

public record Config(
    int port,
    int maxClients,
    long cleanIntervalMs,
    long maxMemoryMb,
    EvictionPolicy evictionPolicy,
    String aofFile,
    FsyncPolicy fsyncPolicy) {

  public boolean aofEnabled() {
    return aofFile != null && !aofFile.isBlank();
  }

  public long maxMemoryBytes() {
    return maxMemoryMb * 1024 * 1024;
  }

  public static Config fromEnv() {
    return new Config(
        envInt("PORT", 6379),
        envInt("POOL_SIZE", 1000),
        envLong("CLEAN_INTERVAL_MS", 10_000),
        envLong("MAX_MEMORY_MB", 0),
        EvictionPolicy.from(System.getenv("EVICTION_POLICY")),
        System.getenv("AOF_FILE"),
        FsyncPolicy.from(System.getenv("AOF_FSYNC")));
  }

  public void log(Logger logger) {
    logger.info("=== Redis Server Configuration ===");
    logger.info("  port            : {}", port);
    logger.info("  max_clients     : {}", maxClients);
    logger.info("  threads         : virtual");
    logger.info("  max_memory      : {}", maxMemoryMb == 0 ? "unlimited" : maxMemoryMb + " MB");
    logger.info("  eviction_policy : {}", evictionPolicy.label());
    logger.info("  aof_enabled     : {}", aofEnabled());
    if (aofEnabled()) {
      logger.info("  aof_file        : {}", aofFile);
      logger.info("  aof_fsync       : {}", fsyncPolicy.name().toLowerCase());
    }
    logger.info("  clean_interval  : {}ms", cleanIntervalMs);
    logger.info("==================================");
  }

  private static int envInt(String name, int defaultValue) {
    String value = System.getenv(name);
    if (value == null) return defaultValue;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static long envLong(String name, long defaultValue) {
    String value = System.getenv(name);
    if (value == null) return defaultValue;
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
