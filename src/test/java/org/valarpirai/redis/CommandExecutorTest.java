package org.valarpirai.redis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandExecutorTest {

  private CommandExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new CommandExecutor(new InMemoryStorage());
  }

  @Test
  void ping() {
    assertEquals("PONG", executor.execute("PING"));
  }

  @Test
  void pingCaseInsensitive() {
    assertEquals("PONG", executor.execute("ping"));
    assertEquals("PONG", executor.execute("Ping"));
  }

  @Test
  void setReturnsOk() {
    assertEquals("OK", executor.execute("SET key value"));
  }

  @Test
  void setAndGet() {
    executor.execute("SET key value");
    assertEquals("value", executor.execute("GET key"));
  }

  @Test
  void getMissingKeyReturnsNil() {
    assertEquals("(nil)", executor.execute("GET missing"));
  }

  @Test
  void setMultiWordValue() {
    executor.execute("SET key hello world");
    assertEquals("hello world", executor.execute("GET key"));
  }

  @Test
  void setCaseInsensitive() {
    assertEquals("OK", executor.execute("set key value"));
  }

  @Test
  void getMissingArgReturnsError() {
    assertTrue(executor.execute("GET").startsWith("-ERR"));
  }

  @Test
  void setMissingArgReturnsError() {
    assertTrue(executor.execute("SET key").startsWith("-ERR"));
  }

  @Test
  void delExistingKey() {
    executor.execute("SET key value");
    assertEquals("1", executor.execute("DEL key"));
  }

  @Test
  void delMissingKey() {
    assertEquals("0", executor.execute("DEL missing"));
  }

  @Test
  void delRemovesKey() {
    executor.execute("SET key value");
    executor.execute("DEL key");
    assertEquals("(nil)", executor.execute("GET key"));
  }

  @Test
  void existsReturnsTrueForPresentKey() {
    executor.execute("SET key value");
    assertEquals("1", executor.execute("EXISTS key"));
  }

  @Test
  void existsReturnsFalseForMissingKey() {
    assertEquals("0", executor.execute("EXISTS missing"));
  }

  @Test
  void unknownCommandReturnsError() {
    assertTrue(executor.execute("FLUSHALL").startsWith("-ERR unknown command"));
  }

  @Test
  void emptyCommandReturnsError() {
    assertTrue(executor.execute("").startsWith("-ERR"));
    assertTrue(executor.execute("   ").startsWith("-ERR"));
  }

  @Test
  void nullCommandReturnsError() {
    assertTrue(executor.execute((String) null).startsWith("-ERR"));
  }

  @Test
  void expireReturnsTrueForExistingKey() {
    executor.execute("SET key value");
    assertEquals("1", executor.execute("EXPIRE key 10"));
  }

  @Test
  void expireReturnsFalseForMissingKey() {
    assertEquals("0", executor.execute("EXPIRE missing 10"));
  }

  @Test
  void expireInvalidSecondReturnsError() {
    executor.execute("SET key value");
    assertTrue(executor.execute("EXPIRE key notanumber").startsWith("-ERR"));
  }

  @Test
  void ttlReturnsMinusOneForKeyWithoutExpiry() {
    executor.execute("SET key value");
    assertEquals("-1", executor.execute("TTL key"));
  }

  @Test
  void ttlReturnsMinusTwoForMissingKey() {
    assertEquals("-2", executor.execute("TTL missing"));
  }

  @Test
  void ttlReturnsRemainingAfterExpire() {
    executor.execute("SET key value");
    executor.execute("EXPIRE key 10");
    long ttl = Long.parseLong(executor.execute("TTL key"));
    assertTrue(ttl > 0 && ttl <= 10);
  }

  @Test
  void expiredKeyReturnsNil() throws InterruptedException {
    executor.execute("SET key value");
    executor.execute("EXPIRE key 0");
    Thread.sleep(10);
    assertEquals("(nil)", executor.execute("GET key"));
  }
}
