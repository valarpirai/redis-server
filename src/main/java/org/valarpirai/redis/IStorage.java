package org.valarpirai.redis;

public interface IStorage {
  String get(String key);

  String set(String key, String value);
}
