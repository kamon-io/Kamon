package org.HdrHistogram

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLongArray

import kamon.metric.instrument.DynamicRange

/**
  * Exposes package-private members of [[org.HdrHistogram.AtomicHistogram]].
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
  * Exposes the package-private members of [[org.HdrHistogram.ZigZagEncoding]].
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

