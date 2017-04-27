package kamon.metric.instrument

import java.util.concurrent.TimeUnit

case class DynamicRange(lowestDiscernibleValue: Long, highestTrackableValue: Long, significantValueDigits: Int) {
  def upTo(highestTrackableValue: Long): DynamicRange =
    copy(highestTrackableValue = highestTrackableValue)

  def startingFrom(lowestDiscernibleValue: Long): DynamicRange =
    copy(lowestDiscernibleValue = lowestDiscernibleValue)
}

object DynamicRange {
  private val oneHourInNanoseconds = TimeUnit.HOURS.toNanos(1)

  /**
    * Provides a range from 0 to 3.6e+12 (one hour in nanoseconds) with a value precision of 1 significant digit (10%)
    * across that range.
    */
  val Loose = DynamicRange(1L, oneHourInNanoseconds, 1)

  /**
    * Provides a range from 0 to 3.6e+12 (one hour in nanoseconds) with a value precision of 2 significant digit (1%)
    * across that range.
    */
  val Default = DynamicRange(1L, oneHourInNanoseconds, 2)

  /**
    * Provides a range from 0 to 3.6e+12 (one hour in nanoseconds) with a value precision of 3 significant digit (0.1%)
    * across that range.
    */
  val Fine = DynamicRange(1L, oneHourInNanoseconds, 3)
}
