package org.valarpirai.redis.storage;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStorage implements IStorage {

  private record Entry(String value, long expiresAt) {
    boolean isExpired() {
      return expiresAt != -1 && System.currentTimeMillis() > expiresAt;
    }
  }

  private final ConcurrentHashMap<String, Entry> storage = new ConcurrentHashMap<>();

  @Override
  public Optional<String> get(String key) {
    Entry entry = storage.get(key);
    if (entry == null) return Optional.empty();
    if (entry.isExpired()) {
      storage.remove(key);
      return Optional.empty();
    }
    return Optional.of(entry.value());
  }

  @Override
  public void set(String key, String value) {
    storage.put(key, new Entry(value, -1));
  }

  @Override
  public boolean delete(String key) {
    Entry entry = storage.remove(key);
    return entry != null && !entry.isExpired();
  }

  @Override
  public boolean exists(String key) {
    return get(key).isPresent();
  }

  @Override
  public boolean expire(String key, long seconds) {
    Entry entry = storage.get(key);
    if (entry == null || entry.isExpired()) return false;
    long expiresAt = System.currentTimeMillis() + seconds * 1000;
    storage.put(key, new Entry(entry.value(), expiresAt));
    return true;
  }

  @Override
  public long ttl(String key) {
    Entry entry = storage.get(key);
    if (entry == null || entry.isExpired()) return -2;
    if (entry.expiresAt() == -1) return -1;
    long remaining = (entry.expiresAt() - System.currentTimeMillis()) / 1000;
    return Math.max(0, remaining);
  }

  @Override
  public boolean expireAt(String key, long epochMs) {
    Entry entry = storage.get(key);
    if (entry == null || entry.isExpired()) return false;
    storage.put(key, new Entry(entry.value(), epochMs));
    return true;
  }

  @Override
  public int cleanExpired() {
    int[] count = {0};
    storage
        .entrySet()
        .removeIf(
            e -> {
              if (e.getValue().isExpired()) {
                count[0]++;
                return true;
              }
              return false;
            });
    return count[0];
  }
}
