package org.valarpirai.redis;

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
  void getReturnsNullForMissingKey() {
    assertNull(storage.get("missing"));
  }

  @Test
  void setAndGet() {
    storage.set("key", "value");
    assertEquals("value", storage.get("key"));
  }

  @Test
  void setOverwritesExistingKey() {
    storage.set("key", "first");
    storage.set("key", "second");
    assertEquals("second", storage.get("key"));
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
    assertNull(storage.get("key"));
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
}
