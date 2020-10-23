/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
