package org.HdrHistogram;

import java.nio.ByteBuffer;

/**
 * Exposes the encoding functions on org.HdrHistogram.ZigZagEncoding.
 */
public class ZigZag {

  public static void putLong(ByteBuffer buffer, long value) {
    ZigZagEncoding.putLong(buffer, value);
  }

  public static long getLong(ByteBuffer buffer) {
    return ZigZagEncoding.getLong(buffer);
  }

  public static void putInt(ByteBuffer buffer, int value) {
    ZigZagEncoding.putInt(buffer, value);
  }

  public static int getInt (ByteBuffer buffer) {
    return ZigZagEncoding.getInt(buffer);
  }
}
