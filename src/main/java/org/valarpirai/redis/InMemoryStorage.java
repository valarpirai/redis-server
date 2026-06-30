package org.valarpirai.redis;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStorage implements IStorage {

  private final ConcurrentHashMap<String, String> storage = new ConcurrentHashMap<>();

  @Override
  public String get(String key) {
    return storage.get(key);
  }

  @Override
  public void set(String key, String value) {
    storage.put(key, value);
  }

  @Override
  public boolean delete(String key) {
    return storage.remove(key) != null;
  }

  @Override
  public boolean exists(String key) {
    return storage.containsKey(key);
  }
}
