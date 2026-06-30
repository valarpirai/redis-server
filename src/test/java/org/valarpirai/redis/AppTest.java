package org.valarpirai.redis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AppTest {

  @Test
  void getEnvIntReturnsDefaultWhenEnvMissing() {
    assertEquals(6379, App.getEnvInt("__NO_SUCH_VAR__", 6379));
  }

  @Test
  void getEnvIntReturnsDefaultOnInvalidValue() {
    assertEquals(5, App.getEnvInt("PATH", 5));
  }
}
