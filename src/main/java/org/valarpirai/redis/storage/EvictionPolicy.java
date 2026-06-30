package org.valarpirai.redis.storage;

public enum EvictionPolicy {
  NOEVICTION,
  ALLKEYS_LRU,
  VOLATILE_LRU;

  public static EvictionPolicy from(String value) {
    if (value == null) return NOEVICTION;
    return switch (value.toLowerCase()) {
      case "allkeys-lru" -> ALLKEYS_LRU;
      case "volatile-lru" -> VOLATILE_LRU;
      default -> NOEVICTION;
    };
  }

  public String label() {
    return switch (this) {
      case ALLKEYS_LRU -> "allkeys-lru";
      case VOLATILE_LRU -> "volatile-lru";
      case NOEVICTION -> "noeviction";
    };
  }
}
