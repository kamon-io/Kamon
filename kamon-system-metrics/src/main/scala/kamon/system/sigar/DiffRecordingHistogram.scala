package kamon.system.sigar

import java.util.concurrent.atomic.AtomicLong

import kamon.metric.instrument.{ CollectionContext, Histogram }

/**
 *  Wrapper Histogram for cases in which the recorded values should always be the difference
 *  between the current value and the last recorded value. This is not thread-safe and only
 *  to be used with Sigar-based metrics that are securely updated within an actor.
 */
class DiffRecordingHistogram(wrappedHistogram: Histogram) extends Histogram {
  @volatile private var _recordedAtLeastOnce = false
  private val _lastObservedValue = new AtomicLong(0)

  private def processRecording(value: Long, count: Long): Unit = {
    if (_recordedAtLeastOnce)
      wrappedHistogram.record(value - _lastObservedValue.getAndSet(value), count)
    else {
      _lastObservedValue.set(value)
      _recordedAtLeastOnce = true
    }
  }

  def record(value: Long): Unit =
    processRecording(value, 1)

  def record(value: Long, count: Long): Unit =
    processRecording(value, count)

  def cleanup: Unit =
    wrappedHistogram.cleanup

  def collect(context: CollectionContext): Histogram.Snapshot =
    wrappedHistogram.collect(context)
}

object DiffRecordingHistogram {
  def apply(histogram: Histogram): DiffRecordingHistogram =
    new DiffRecordingHistogram(histogram)
}