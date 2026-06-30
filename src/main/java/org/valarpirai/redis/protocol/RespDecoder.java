package org.valarpirai.redis.protocol;

import java.io.BufferedReader;
import java.io.IOException;

public class RespDecoder {

  private RespDecoder() {}

  /**
   * Reads one RESP command from the reader. Returns tokens as a String array, or null on EOF. Falls
   * back to plain-text line splitting when the input is not a RESP array (supports nc/telnet
   * clients).
   */
  public static String[] decode(BufferedReader reader) throws IOException {
    String line = reader.readLine();
    if (line == null) return null;

    if (line.startsWith("*")) {
      int count = Integer.parseInt(line.substring(1));
      String[] tokens = new String[count];
      for (int i = 0; i < count; i++) {
        String bulkHeader = reader.readLine();
        if (bulkHeader == null || !bulkHeader.startsWith("$")) {
          throw new IOException("Expected bulk string header, got: " + bulkHeader);
        }
        tokens[i] = reader.readLine();
      }
      return tokens;
    }

    // plain-text fallback (nc, telnet, integration tests before full RESP)
    return line.trim().split("\\s+");
  }
}
