package org.valarpirai.redis;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStorage implements IStorage {

  private ConcurrentHashMap<String, String> storage = new ConcurrentHashMap<>();

  @Override
  public String get(String key) {

    return storage.get(key);
  }

  @Override
  public String set(String key, String value) {
    storage.put(key, value);
    return "1";
  }
}
