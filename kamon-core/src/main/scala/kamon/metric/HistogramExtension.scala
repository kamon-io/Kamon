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
import kamon.metric.DynamicRange


trait HdrHistogramOps {
  def getCountsArraySize(): Int
  def getFromCountsArray(index: Int): Long
  def getAndSetFromCountsArray(index: Int, newValue: Long): Long
  def protectedUnitMagnitude(): Int
  def protectedSubBucketHalfCount(): Int
  def protectedSubBucketHalfCountMagnitude(): Int
}

/**
  * Exposes package-private members of org.HdrHistogram.AtomicHistogram.
  */
abstract class AtomicHistogramExtension(dr: DynamicRange)
    extends AtomicHistogram(dr.lowestDiscernibleValue, dr.highestTrackableValue, dr.significantValueDigits) with HdrHistogramOps {

  override def incrementTotalCount(): Unit = {}
  override def addToTotalCount(value: Long): Unit = {}

  override def getCountsArraySize(): Int = counts.length()
  override def getFromCountsArray(index: Int): Long = counts.get(index)
  override def getAndSetFromCountsArray(index: Int, newValue: Long): Long = counts.getAndSet(index, newValue)

  override def protectedUnitMagnitude(): Int = unitMagnitude
  override def protectedSubBucketHalfCount(): Int = subBucketHalfCount
  override def protectedSubBucketHalfCountMagnitude(): Int = subBucketHalfCountMagnitude
}


/**
  * Exposes package-private members of org.HdrHistogram.AtomicHistogram.
  */
abstract class SimpleHistogramExtension(dr: DynamicRange)
  extends Histogram(dr.lowestDiscernibleValue, dr.highestTrackableValue, dr.significantValueDigits) with HdrHistogramOps {

  override def incrementTotalCount(): Unit = {}
  override def addToTotalCount(value: Long): Unit = {}

  override def getCountsArraySize(): Int = counts.length
  override def getFromCountsArray(index: Int): Long = getCountAtIndex(index)
  override def getAndSetFromCountsArray(index: Int, newValue: Long): Long = {
    val v = getCountAtIndex(index)
    setCountAtIndex(index, newValue)
    v
  }

  override def protectedUnitMagnitude(): Int = unitMagnitude
  override def protectedSubBucketHalfCount(): Int = subBucketHalfCount
  override def protectedSubBucketHalfCountMagnitude(): Int = subBucketHalfCountMagnitude
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

