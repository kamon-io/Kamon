/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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

package kamon.util

// Extracted from https://github.com/openzipkin/brave/blob/master/brave/src/main/java/brave/internal/HexCodec.java
object HexCodec {

  /**
    * Parses a 1 to 32 character lower-hex string with no prefix into an unsigned long, tossing any
    * bits higher than 64.
    */
  def lowerHexToUnsignedLong(lowerHex: String): Long = {
    val length = lowerHex.length
    if (length < 1 || length > 32) throw isntLowerHexLong(lowerHex)
    // trim off any high bits
    val beginIndex = if (length > 16) length - 16
    else 0
    lowerHexToUnsignedLong(lowerHex, beginIndex)
  }

  private def isntLowerHexLong(lowerHex: String) =
    throw new NumberFormatException(lowerHex + " should be a 1 to 32 character lower-hex string with no prefix")

  /**
    * Parses a 16 character lower-hex string with no prefix into an unsigned long, starting at the
    * spe index.
    */
  private def lowerHexToUnsignedLong(lowerHex: String, index: Int): Long = {
    var i = index
    var result = 0L
    val endIndex = Math.min(index + 16, lowerHex.length)
    while (i < endIndex) {
      val c = lowerHex.charAt(i)
      result <<= 4
      if (c >= '0' && c <= '9')
        result |= c - '0'
      else if (c >= 'a' && c <= 'f')
        result |= c - 'a' + 10
      else throw isntLowerHexLong(lowerHex)

      i += 1
    }
    result
  }

  /**
    * Inspired by {@code okio.Buffer.writeLong}
    */
  def toLowerHex(v: Long): String = {
    val data = new Array[Char](16)
    writeHexLong(data, 0, v)
    new String(data)
  }

  private def writeHexLong(data: Array[Char], pos: Int, v: Long): Unit = {
    writeHexByte(data, pos + 0, ((v >>> 56L) & 0xff).toByte)
    writeHexByte(data, pos + 2, ((v >>> 48L) & 0xff).toByte)
    writeHexByte(data, pos + 4, ((v >>> 40L) & 0xff).toByte)
    writeHexByte(data, pos + 6, ((v >>> 32L) & 0xff).toByte)
    writeHexByte(data, pos + 8, ((v >>> 24L) & 0xff).toByte)
    writeHexByte(data, pos + 10, ((v >>> 16L) & 0xff).toByte)
    writeHexByte(data, pos + 12, ((v >>> 8L) & 0xff).toByte)
    writeHexByte(data, pos + 14, (v & 0xff).toByte)
  }

  private val HEX_DIGITS = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  private def writeHexByte(data: Array[Char], pos: Int, b: Byte): Unit = {
    data(pos + 0) = HEX_DIGITS((b >> 4) & 0xf)
    data(pos + 1) = HEX_DIGITS(b & 0xf)
  }
}
