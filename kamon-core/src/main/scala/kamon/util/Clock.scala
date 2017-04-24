package kamon.util

trait Clock {
  def nanoTimestamp(): Long
  def microTimestamp(): Long
  def milliTimestamp(): Long

  def relativeNanoTimestamp(): Long
}
