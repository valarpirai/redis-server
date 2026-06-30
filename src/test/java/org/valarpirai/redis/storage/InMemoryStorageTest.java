package org.valarpirai.redis.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryStorageTest {

  private InMemoryStorage storage;

  @BeforeEach
  void setUp() {
    storage = new InMemoryStorage();
  }

  @Test
  void getReturnsEmptyForMissingKey() {
    assertTrue(storage.get("missing").isEmpty());
  }

  @Test
  void setAndGet() {
    storage.set("key", "value");
    assertEquals("value", storage.get("key").orElseThrow());
  }

  @Test
  void setOverwritesExistingKey() {
    storage.set("key", "first");
    storage.set("key", "second");
    assertEquals("second", storage.get("key").orElseThrow());
  }

  @Test
  void deleteExistingKeyReturnsTrue() {
    storage.set("key", "value");
    assertTrue(storage.delete("key"));
  }

  @Test
  void deleteMissingKeyReturnsFalse() {
    assertFalse(storage.delete("missing"));
  }

  @Test
  void deleteRemovesKey() {
    storage.set("key", "value");
    storage.delete("key");
    assertTrue(storage.get("key").isEmpty());
  }

  @Test
  void existsReturnsTrueForPresentKey() {
    storage.set("key", "value");
    assertTrue(storage.exists("key"));
  }

  @Test
  void existsReturnsFalseForMissingKey() {
    assertFalse(storage.exists("missing"));
  }

  @Test
  void existsReturnsFalseAfterDelete() {
    storage.set("key", "value");
    storage.delete("key");
    assertFalse(storage.exists("key"));
  }

  @Test
  void ttlReturnsMinusOneForKeyWithoutExpiry() {
    storage.set("key", "value");
    assertEquals(-1, storage.ttl("key"));
  }

  @Test
  void ttlReturnsMinusTwoForMissingKey() {
    assertEquals(-2, storage.ttl("missing"));
  }

  @Test
  void expireReturnsFalseForMissingKey() {
    assertFalse(storage.expire("missing", 10));
  }

  @Test
  void expireReturnsTrueForExistingKey() {
    storage.set("key", "value");
    assertTrue(storage.expire("key", 10));
  }

  @Test
  void ttlReturnRemainingSecondsAfterExpire() {
    storage.set("key", "value");
    storage.expire("key", 10);
    long remaining = storage.ttl("key");
    assertTrue(remaining > 0 && remaining <= 10);
  }

  @Test
  void expiredKeyIsInvisibleToGet() throws InterruptedException {
    storage.set("key", "value");
    storage.expire("key", 0);
    Thread.sleep(10);
    assertTrue(storage.get("key").isEmpty());
  }

  @Test
  void expiredKeyIsInvisibleToExists() throws InterruptedException {
    storage.set("key", "value");
    storage.expire("key", 0);
    Thread.sleep(10);
    assertFalse(storage.exists("key"));
  }

  @Test
  void expireAtWithFutureTimestamp() {
    storage.set("key", "value");
    long epochMs = System.currentTimeMillis() + 10_000;
    assertTrue(storage.expireAt("key", epochMs));
    assertTrue(storage.get("key").isPresent());
  }

  @Test
  void expireAtWithPastTimestampExpires() throws InterruptedException {
    storage.set("key", "value");
    storage.expireAt("key", System.currentTimeMillis() - 1);
    Thread.sleep(10);
    assertTrue(storage.get("key").isEmpty());
  }

  @Test
  void expireAtReturnsFalseForMissingKey() {
    assertFalse(storage.expireAt("missing", System.currentTimeMillis() + 10_000));
  }

  @Test
  void cleanExpiredRemovesExpiredKeys() throws InterruptedException {
    storage.set("a", "1");
    storage.set("b", "2");
    storage.expire("a", 0);
    Thread.sleep(10);
    int evicted = storage.cleanExpired();
    assertEquals(1, evicted);
    assertTrue(storage.get("b").isPresent());
  }

  @Test
  void cleanExpiredIgnoresLiveKeys() {
    storage.set("a", "1");
    storage.set("b", "2");
    assertEquals(0, storage.cleanExpired());
  }

  @Test
  void cleanExpiredReturnsZeroWhenEmpty() {
    assertEquals(0, storage.cleanExpired());
  }

  @Test
  void sizeReturnsLiveKeyCount() {
    storage.set("a", "1");
    storage.set("b", "2");
    assertEquals(2, storage.size());
  }

  @Test
  void sizeExcludesExpiredKeys() throws InterruptedException {
    storage.set("a", "1");
    storage.set("b", "2");
    storage.expire("a", 0);
    Thread.sleep(10);
    assertEquals(1, storage.size());
  }

  @Test
  void sizeIsZeroWhenEmpty() {
    assertEquals(0, storage.size());
  }
}
