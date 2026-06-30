package org.valarpirai.redis.storage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExpiryCleanerWorker {

  private static final Logger log = LoggerFactory.getLogger(ExpiryCleanerWorker.class);

  private final ScheduledExecutorService scheduler;
  private final IStorage storage;
  private final long intervalMs;

  public ExpiryCleanerWorker(IStorage storage, long intervalMs) {
    this.storage = storage;
    this.intervalMs = intervalMs;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "expiry-cleaner");
              t.setDaemon(true);
              return t;
            });
  }

  public void start() {
    scheduler.scheduleAtFixedRate(this::clean, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    log.info("Expiry cleaner started (interval={}ms)", intervalMs);
  }

  public void shutdown() {
    scheduler.shutdown();
  }

  private void clean() {
    int evicted = storage.cleanExpired();
    if (evicted > 0) {
      log.debug("Evicted {} expired keys", evicted);
    }
  }
}
