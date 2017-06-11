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

package org.HdrHistogram

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLongArray

import kamon.metric.DynamicRange

/**
  * Exposes package-private members of org.HdrHistogram.AtomicHistogram.
  */
abstract class AtomicHistogramExtension(dr: DynamicRange)
    extends AtomicHistogram(dr.lowestDiscernibleValue, dr.highestTrackableValue, dr.significantValueDigits) {

  override def incrementTotalCount(): Unit = {}
  override def addToTotalCount(value: Long): Unit = {}

  def countsArray(): AtomicLongArray = counts
  def protectedUnitMagnitude(): Int = unitMagnitude
  def protectedSubBucketHalfCount(): Int = subBucketHalfCount
  def protectedSubBucketHalfCountMagnitude(): Int = subBucketHalfCountMagnitude
}

/**
  * Exposes the package-private members of org.HdrHistogram.ZigZagEncoding.
  */
object ZigZag {
  def putLong(buffer: ByteBuffer, value: Long): Unit =
    ZigZagEncoding.putLong(buffer, value)

  def getLong(buffer: ByteBuffer): Long =
    ZigZagEncoding.getLong(buffer)

  def putInt(buffer: ByteBuffer, value: Int): Unit =
    ZigZagEncoding.putInt(buffer, value)

  def getInt(buffer: ByteBuffer): Int =
    ZigZagEncoding.getInt(buffer)
}

