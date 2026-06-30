package org.valarpirai.redis.command;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.valarpirai.redis.server.ServerStats;
import org.valarpirai.redis.storage.AofWriter;
import org.valarpirai.redis.storage.EvictionPolicy;
import org.valarpirai.redis.storage.InMemoryStorage;

class CommandExecutorTest {

  @TempDir Path tempDir;

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

  @Test
  void expireAtReturnsTrueForExistingKey() {
    executor.execute("SET key value");
    long future = System.currentTimeMillis() + 10_000;
    assertEquals("1", executor.execute("EXPIREAT key " + future));
  }

  @Test
  void expireAtReturnsFalseForMissingKey() {
    long future = System.currentTimeMillis() + 10_000;
    assertEquals("0", executor.execute("EXPIREAT missing " + future));
  }

  @Test
  void expireAtWithPastTimestampExpiresKey() throws InterruptedException {
    executor.execute("SET key value");
    executor.execute("EXPIREAT key " + (System.currentTimeMillis() - 1));
    Thread.sleep(10);
    assertEquals("(nil)", executor.execute("GET key"));
  }

  @Test
  void expireAtInvalidTimestampReturnsError() {
    executor.execute("SET key value");
    assertTrue(executor.execute("EXPIREAT key notanumber").startsWith("-ERR"));
  }

  @Test
  void setWritesToAof() throws IOException {
    Path aofFile = tempDir.resolve("test.aof");
    try (var aofWriter = new AofWriter(aofFile.toString())) {
      var exec = new CommandExecutor(new InMemoryStorage(), aofWriter);
      exec.execute("SET foo bar");
    }
    String content = Files.readString(aofFile);
    assertTrue(content.contains("SET"));
    assertTrue(content.contains("foo"));
    assertTrue(content.contains("bar"));
  }

  @Test
  void delWritesToAofOnlyWhenKeyExists() throws IOException {
    Path aofFile = tempDir.resolve("test.aof");
    try (var aofWriter = new AofWriter(aofFile.toString())) {
      var exec = new CommandExecutor(new InMemoryStorage(), aofWriter);
      exec.execute("DEL missing");
    }
    assertEquals(0, Files.size(aofFile));
  }

  @Test
  void expireWritesExpireatToAof() throws IOException {
    Path aofFile = tempDir.resolve("test.aof");
    try (var aofWriter = new AofWriter(aofFile.toString())) {
      var exec = new CommandExecutor(new InMemoryStorage(), aofWriter);
      exec.execute("SET key value");
      exec.execute("EXPIRE key 60");
    }
    String content = Files.readString(aofFile);
    assertTrue(content.contains("EXPIREAT"));
  }

  @Test
  void statsCommandReturnsBulkString() {
    var stats = new ServerStats();
    var exec = new CommandExecutor(new InMemoryStorage(), null, stats);
    exec.execute("SET a 1");
    exec.execute("SET b 2");
    String result = exec.execute("STATS");
    assertTrue(result.contains("total_commands_processed:3"));
    assertTrue(result.contains("total_keys:2"));
    assertTrue(result.contains("aof_enabled:0"));
  }

  @Test
  void statsCommandCountsCommands() {
    var stats = new ServerStats();
    var exec = new CommandExecutor(new InMemoryStorage(), null, stats);
    exec.execute("PING");
    exec.execute("PING");
    String result = exec.execute("STATS");
    assertTrue(result.contains("total_commands_processed:3"));
  }

  @Test
  void statsCommandWithoutStatsObjectStillReturns() {
    String result = executor.execute("STATS");
    assertTrue(result.contains("uptime_seconds:"));
    assertTrue(result.contains("total_keys:"));
  }

  @Test
  void setReturnsOomWhenMaxMemoryExceededWithNoeviction() {
    var storage = new InMemoryStorage();
    storage.set("seed", "data");
    long limit = storage.usedMemoryBytes() - 1; // already over limit
    var exec = new CommandExecutor(storage, null, null, limit, EvictionPolicy.NOEVICTION);
    assertTrue(exec.execute("SET key value").startsWith("-OOM"));
  }

  @Test
  void setEvictsKeyWhenMaxMemoryExceededWithLru() {
    var storage = new InMemoryStorage();
    storage.set("old", "value");
    long limit = storage.usedMemoryBytes();
    var exec = new CommandExecutor(storage, null, null, limit, EvictionPolicy.ALLKEYS_LRU);
    assertEquals("OK", exec.execute("SET new value"));
  }

  @Test
  void statsIncludesMemoryFields() {
    String result = executor.execute("STATS");
    assertTrue(result.contains("used_memory_bytes:"));
    assertTrue(result.contains("max_memory_bytes:0"));
    assertTrue(result.contains("eviction_policy:noeviction"));
  }

  @Test
  void keysReturnsAllKeysForWildcard() {
    executor.execute("SET foo 1");
    executor.execute("SET bar 2");
    executor.execute("SET baz 3");
    CommandResult result = executor.executeCommand(new String[] {"KEYS", "*"});
    assertInstanceOf(CommandResult.Array.class, result);
    var arr = ((CommandResult.Array) result).elements();
    assertEquals(3, arr.size());
  }

  @Test
  void keysFiltersWithGlobPattern() {
    executor.execute("SET hello 1");
    executor.execute("SET hallo 2");
    executor.execute("SET world 3");
    CommandResult result = executor.executeCommand(new String[] {"KEYS", "h?llo"});
    var arr = ((CommandResult.Array) result).elements();
    assertEquals(2, arr.size());
  }

  @Test
  void keysPrefixPattern() {
    executor.execute("SET user:1 a");
    executor.execute("SET user:2 b");
    executor.execute("SET session:1 c");
    CommandResult result = executor.executeCommand(new String[] {"KEYS", "user:*"});
    var arr = ((CommandResult.Array) result).elements();
    assertEquals(2, arr.size());
  }

  @Test
  void keysReturnsEmptyArrayWhenNoMatch() {
    executor.execute("SET foo 1");
    CommandResult result = executor.executeCommand(new String[] {"KEYS", "z*"});
    var arr = ((CommandResult.Array) result).elements();
    assertTrue(arr.isEmpty());
  }

  @Test
  void scanReturnsAllKeysOverMultipleCalls() {
    for (int i = 0; i < 25; i++) executor.execute("SET key:" + i + " v");
    int cursor = 0;
    int total = 0;
    do {
      CommandResult result = executor.executeCommand(new String[] {"SCAN", String.valueOf(cursor)});
      var outer = ((CommandResult.Array) result).elements();
      cursor = Integer.parseInt(((CommandResult.Bulk) outer.get(0)).value());
      total += ((CommandResult.Array) outer.get(1)).elements().size();
    } while (cursor != 0);
    assertEquals(25, total);
  }

  @Test
  void scanWithMatchFiltersResults() {
    executor.execute("SET user:1 a");
    executor.execute("SET user:2 b");
    executor.execute("SET other 3");
    CommandResult result =
        executor.executeCommand(new String[] {"SCAN", "0", "MATCH", "user:*", "COUNT", "100"});
    var outer = ((CommandResult.Array) result).elements();
    var keys = ((CommandResult.Array) outer.get(1)).elements();
    assertEquals(2, keys.size());
  }

  @Test
  void scanCursorZeroOnCompleteIteration() {
    executor.execute("SET a 1");
    CommandResult result = executor.executeCommand(new String[] {"SCAN", "0", "COUNT", "100"});
    var outer = ((CommandResult.Array) result).elements();
    assertEquals("0", ((CommandResult.Bulk) outer.get(0)).value());
  }
}
