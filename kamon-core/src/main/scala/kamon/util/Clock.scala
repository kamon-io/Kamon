package kamon.util

object Clock {
  private val startTimeMillis = System.currentTimeMillis()
  private val startNanoTime = System.nanoTime()
  private val startMicroTime = startTimeMillis * 1000L

  def microTimestamp(): Long =
    startMicroTime + ((System.nanoTime() - startNanoTime) / 1000L)

  def milliTimestamp(): Long =
    System.currentTimeMillis()

  def relativeNanoTimestamp(): Long =
    System.nanoTime()
}