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

package kamon
package util

import java.time.{Duration, Instant, ZoneId, Clock => JavaClock}

/**
  * Extension on Java's Clock that exposes additional APIs to work with nanoTime values.
  */
abstract class Clock extends JavaClock {

  /**
    * Returns the current value of the high-resolution time source, in nanoseconds.
    */
  def nanos(): Long

  /**
    * Creates a new Instant from the provided high-resolution time source value.
    */
  def toInstant(nanos: Long): Instant

  /**
    * Returns the difference in nanoseconds between the current instant and the provided one.
    */
  def nanosSince(instant: Instant): Long

}

object Clock {

  private val _nanosInMicro = 1000L
  private val _millisInSecond = 1000L
  private val _microsInSecond = 1000000L
  private val _nanosInSecond = 1000000000L

  /**
    * Clock implementation that creates an anchor between the time sources used by `System.nanoTime` and
    * `System.currentTimeMillis` to create Instant instances that properly reflect the current system time (as is always
    * the case with `System.currentTimeMillis`) but with better performance characteristics, given that the current
    * Instant can be derived using a call to `System.nanoTime`, which is considerably faster.
    */
  class Anchored extends Clock {
    private val _systemClock = JavaClock.systemUTC()
    private val (_startTimeMillis, _startNanoTime) = {
      var calibrationIterations = 1000
      var millis = System.currentTimeMillis()
      var nanos = System.nanoTime()
      var isCandidate = false

      while (calibrationIterations > 0) {
        val currentMillis = System.currentTimeMillis()
        val currentNanos = System.nanoTime()

        if (isCandidate && millis != currentMillis) {
          millis = currentMillis
          nanos = currentNanos
          calibrationIterations = 0
        } else {
          if (millis == currentMillis) {
            isCandidate = true
          } else {
            millis = currentMillis
            nanos = currentNanos
          }
        }

        calibrationIterations -= 1
      }

      (millis, nanos)
    }

    private val _startSecondTime = Math.floorDiv(_startTimeMillis, _millisInSecond)
    private val _startSecondNanoOffset =
      Math.multiplyExact(Math.floorMod(_startTimeMillis, _millisInSecond), _microsInSecond)

    override def nanos(): Long =
      System.nanoTime()

    override def toInstant(nanos: Long): Instant = {
      val nanoOffset = nanos - _startNanoTime + _startSecondNanoOffset
      Instant.ofEpochSecond(_startSecondTime, nanoOffset)
    }

    override def instant(): Instant =
      toInstant(System.nanoTime())

    override def withZone(zone: ZoneId): JavaClock =
      _systemClock.withZone(zone)

    override def getZone: ZoneId =
      _systemClock.getZone()

    override def nanosSince(instant: Instant): Long = {
      nanosBetween(instant, this.instant())
    }
  }

  /**
    * Returns the number of nanoseconds between two instants.
    */
  def nanosBetween(left: Instant, right: Instant): Long = {
    val secsDiff = Math.subtractExact(right.getEpochSecond, left.getEpochSecond)
    val totalNanos = Math.multiplyExact(secsDiff, _nanosInSecond)
    Math.addExact(totalNanos, right.getNano - left.getNano)
  }

  /**
    * Returns the number of microseconds between the EPOCH and the provided instant.
    */
  def toEpochMicros(instant: Instant): Long = {
    Math.multiplyExact(instant.getEpochSecond, _microsInSecond) + Math.floorDiv(instant.getNano, _nanosInMicro)
  }

  /**
    * Returns the next Instant that aligns with the provided bucket size duration. For example, if this function is
    * called with an instant of 11:34:02 AM and a bucket size of 20 seconds, the returned Instant will be 11:34:20 AM,
    * which is the next Instant that perfectly aligns with 20 second bucket boundaries from the EPOCH.
    */
  def nextAlignedInstant(from: Instant, bucketSize: Duration): Instant = {
    val fromMillis = from.toEpochMilli()
    val intervalCount = Math.floorDiv(fromMillis, bucketSize.toMillis)
    val nextTickMillis = bucketSize.toMillis * (intervalCount + 1)

    Instant.ofEpochMilli(nextTickMillis)
  }
}
