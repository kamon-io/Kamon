package kamon.trace

import java.util.concurrent.atomic.AtomicLong

import kamon.NanoInterval
import scala.concurrent.forkjoin.ThreadLocalRandom

trait Sampler {
  def shouldTrace: Boolean
  def shouldReport(traceElapsedTime: NanoInterval): Boolean
}

object NoSampling extends Sampler {
  def shouldTrace: Boolean = false
  def shouldReport(traceElapsedTime: NanoInterval): Boolean = false
}

object SampleAll extends Sampler {
  def shouldTrace: Boolean = true
  def shouldReport(traceElapsedTime: NanoInterval): Boolean = true
}

class RandomSampler(chance: Int) extends Sampler {
  require(chance > 0, "kamon.trace.random-sampler.chance cannot be <= 0")
  require(chance <= 100, "kamon.trace.random-sampler.chance cannot be > 100")

  def shouldTrace: Boolean = ThreadLocalRandom.current().nextInt(100) <= chance
  def shouldReport(traceElapsedTime: NanoInterval): Boolean = true
}

class OrderedSampler(interval: Int) extends Sampler {
  require(interval > 0, "kamon.trace.ordered-sampler.interval cannot be <= 0")

  private val counter = new AtomicLong(0L)
  def shouldTrace: Boolean = counter.incrementAndGet() % interval == 0
  // TODO: find a more efficient way to do this, protect from long overflow.
  def shouldReport(traceElapsedTime: NanoInterval): Boolean = true
}

class ThresholdSampler(thresholdInNanoseconds: Long) extends Sampler {
  require(thresholdInNanoseconds > 0, "kamon.trace.threshold-sampler.minimum-elapsed-time cannot be <= 0")

  def shouldTrace: Boolean = true
  def shouldReport(traceElapsedTime: NanoInterval): Boolean = traceElapsedTime.nanos >= thresholdInNanoseconds
}

