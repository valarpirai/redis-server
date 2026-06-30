package org.valarpirai.redis.storage;

public enum FsyncPolicy {
  ALWAYS,
  EVERYSEC,
  NO;

  public static FsyncPolicy from(String value) {
    if (value == null) return EVERYSEC;
    return switch (value.toLowerCase()) {
      case "always" -> ALWAYS;
      case "no" -> NO;
      default -> EVERYSEC;
    };
  }
}
