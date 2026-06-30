package org.valarpirai.redis.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStorage implements IStorage {

  private record Entry(String value, long expiresAt, long lastAccessMs) {
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
    storage.replace(key, entry, new Entry(entry.value(), entry.expiresAt(), now()));
    return Optional.of(entry.value());
  }

  @Override
  public void set(String key, String value) {
    storage.put(key, new Entry(value, -1, now()));
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
    storage.put(key, new Entry(entry.value(), now() + seconds * 1000, entry.lastAccessMs()));
    return true;
  }

  @Override
  public long ttl(String key) {
    Entry entry = storage.get(key);
    if (entry == null || entry.isExpired()) return -2;
    if (entry.expiresAt() == -1) return -1;
    long remaining = (entry.expiresAt() - now()) / 1000;
    return Math.max(0, remaining);
  }

  @Override
  public boolean expireAt(String key, long epochMs) {
    Entry entry = storage.get(key);
    if (entry == null || entry.isExpired()) return false;
    storage.put(key, new Entry(entry.value(), epochMs, entry.lastAccessMs()));
    return true;
  }

  @Override
  public int size() {
    return (int) storage.values().stream().filter(e -> !e.isExpired()).count();
  }

  @Override
  public long usedMemoryBytes() {
    return storage.entrySet().stream()
        .filter(e -> !e.getValue().isExpired())
        .mapToLong(e -> e.getKey().length() * 2L + e.getValue().value().length() * 2L)
        .sum();
  }

  @Override
  public boolean evict(EvictionPolicy policy) {
    if (policy == EvictionPolicy.NOEVICTION) return false;

    List<Map.Entry<String, Entry>> candidates = new ArrayList<>();
    int sampleSize = Math.min(10, storage.size());
    int sampled = 0;
    for (var e : storage.entrySet()) {
      if (sampled >= sampleSize) break;
      if (e.getValue().isExpired()) continue;
      if (policy == EvictionPolicy.VOLATILE_LRU && e.getValue().expiresAt() == -1) continue;
      candidates.add(e);
      sampled++;
    }

    if (candidates.isEmpty()) return false;

    Map.Entry<String, Entry> victim =
        candidates.stream()
            .min(Comparator.comparingLong(e -> e.getValue().lastAccessMs()))
            .orElse(null);

    if (victim == null) return false;
    storage.remove(victim.getKey());
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

  private static long now() {
    return System.currentTimeMillis();
  }
}
