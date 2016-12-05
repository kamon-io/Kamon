package kamon.bench.instruments

import java.nio.LongBuffer
import java.util.concurrent.TimeUnit

import kamon.metric.instrument.Histogram.DynamicRange
import kamon.metric.instrument.{CollectionContext, Histogram, PaddedMinMaxCounter}
import org.openjdk.jmh.annotations._

@State(Scope.Group)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
class KamonMinMaxCounterBench {

  val counter: PaddedMinMaxCounter = new PaddedMinMaxCounter(Histogram(DynamicRange(1L, 999999999L, 2)))

  val collectionContext = new CollectionContext {
    val buffer: LongBuffer = LongBuffer.allocate(33792)
  }

  @Benchmark
  @Group("rw")
  def increment(): Unit = {
    counter.increment()
  }

  @Benchmark
  @Group("rw")
  def get(): Long = {
    counter.collect(collectionContext).min
  }
}
