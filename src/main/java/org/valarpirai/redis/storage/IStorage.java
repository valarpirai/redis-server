package org.valarpirai.redis.storage;

import java.util.Optional;

public interface IStorage {
  Optional<String> get(String key);

  void set(String key, String value);

  boolean delete(String key);

  boolean exists(String key);

  /** Sets TTL in seconds. Returns false if the key does not exist. */
  boolean expire(String key, long seconds);

  /** Returns seconds remaining, -1 if no expiry, -2 if key missing. */
  long ttl(String key);

  /** Sets expiry as an absolute epoch millisecond. Returns false if the key does not exist. */
  boolean expireAt(String key, long epochMs);

  /** Removes all expired keys. Returns the number of keys evicted. */
  int cleanExpired();

  /** Returns the number of live (non-expired) keys. */
  int size();
}
