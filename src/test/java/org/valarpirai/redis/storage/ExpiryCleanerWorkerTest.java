package org.valarpirai.redis.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExpiryCleanerWorkerTest {

  @Test
  void cleanerEvictsExpiredKeysOnSchedule() throws InterruptedException {
    var storage = new InMemoryStorage();
    storage.set("a", "1");
    storage.set("b", "2");
    storage.expire("a", 0);

    var cleaner = new ExpiryCleanerWorker(storage, 50);
    cleaner.start();
    Thread.sleep(200);
    cleaner.shutdown();

    assertTrue(storage.get("a").isEmpty());
    assertTrue(storage.get("b").isPresent());
  }

  @Test
  void cleanerDoesNotEvictLiveKeys() throws InterruptedException {
    var storage = new InMemoryStorage();
    storage.set("key", "value");

    var cleaner = new ExpiryCleanerWorker(storage, 50);
    cleaner.start();
    Thread.sleep(200);
    cleaner.shutdown();

    assertTrue(storage.get("key").isPresent());
  }
}
