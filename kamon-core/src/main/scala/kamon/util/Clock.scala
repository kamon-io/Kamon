/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.util

import java.time.{Instant, ZoneId, Clock => JavaClock}

abstract class Clock extends JavaClock {
  def nanos(): Long
  def nanosBetween(left: Instant, right: Instant): Long
  def toInstant(nanos: Long): Instant
}

object Clock {

  private val MillisInSecond = 1000L
  private val MicrosInSecond = 1000000L
  private val NanosInSecond = 1000000000L

  class Default extends Clock {
    private val systemClock = JavaClock.systemUTC()
    private val (startTimeMillis, startNanoTime) = {
      var calibrationIterations = 1000
      var millis = System.currentTimeMillis()
      var nanos = System.nanoTime()
      var isCandidate = false

      while(calibrationIterations > 0) {
        val currentMillis = System.currentTimeMillis()
        val currentNanos = System.nanoTime()

        if(isCandidate && millis != currentMillis) {
          millis = currentMillis
          nanos = currentNanos
          calibrationIterations = 0
        } else {
          if(millis == currentMillis) {
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

    private val startSecondTime = Math.floorDiv(startTimeMillis, MillisInSecond)
    private val startSecondNanoOffset = Math.multiplyExact(Math.floorMod(startTimeMillis, MillisInSecond), MicrosInSecond)

    override def nanos(): Long =
      System.nanoTime()

    override def toInstant(nanos: Long): Instant = {
      val nanoOffset = nanos - startNanoTime + startSecondNanoOffset
      Instant.ofEpochSecond(startSecondTime, nanoOffset)
    }

    override def instant(): Instant =
      toInstant(System.nanoTime())

    override def nanosBetween(left: Instant, right: Instant): Long = {
      val secsDiff = Math.subtractExact(right.getEpochSecond, left.getEpochSecond)
      val totalNanos = Math.multiplyExact(secsDiff, NanosInSecond)
      return Math.addExact(totalNanos, right.getNano - left.getNano)
    }

    override def withZone(zone: ZoneId): JavaClock =
      systemClock.withZone(zone)

    override def getZone: ZoneId =
      systemClock.getZone()
  }
}