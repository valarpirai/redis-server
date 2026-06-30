package org.valarpirai.redis;

public interface IStorage {
  String get(String key);

  void set(String key, String value);

  boolean delete(String key);

  boolean exists(String key);
}
