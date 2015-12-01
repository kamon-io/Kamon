/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.metric

import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{InstrumentFactory, Memory, Time, UnitOfMeasurement}
import kamon.testkit.BaseKamonSpec
import kamon.util.MilliTimestamp
import org.scalatest.OptionValues._

class MetricScaleDecoratorSpec extends BaseKamonSpec("metrics-scale-decorator-spec") with SnapshotFixtures {
  "the MetricScaleDecorator" when {
    "receives a snapshot" which {

      val scaleDecorator = system.actorOf(MetricScaleDecorator.props(
        Some(Time.Milliseconds), Some(Memory.KiloBytes), testActor))
      "is empty" should {
        "do nothing for empty snapshots" in {
          scaleDecorator ! emptySnapshot
          expectMsg(emptySnapshot)
        }
      }
      "is non empty" should {
        scaleDecorator ! nonEmptySnapshot
        val scaled = expectMsgType[TickMetricSnapshot]
        val snapshot = scaled.metrics(testEntity)

        "scale time metrics" in {
          snapshot.histogram("nano-time").value.max should be(10L +- 1L)
          snapshot.counter("micro-time").value.count should be(1000L)
        }
        "scale memory metrics" in {
          snapshot.histogram("byte-memory").value.max should be(1)
          snapshot.counter("kbyte-memory").value.count should be(100L)
        }
        "do nothing with unknown metrics" in {
          snapshot.histogram("unknown-histogram").value.max should be(1000L)
          snapshot.counter("unknown-counter").value.count should be(10L)
        }
        "not change from and to" in {
          scaled.from.millis should be(1000)
          scaled.to.millis should be(2000)
        }
      }
    }
  }
}

trait SnapshotFixtures {
  self: BaseKamonSpec =>

  class ScaleDecoratorTestMetrics(instrumentFactory: InstrumentFactory)
    extends GenericEntityRecorder(instrumentFactory) {
    val nanoTime = histogram("nano-time", Time.Nanoseconds)
    val microTime = counter("micro-time", Time.Microseconds)
    val byteMemory = histogram("byte-memory", Memory.Bytes)
    val kbyteMemory = counter("kbyte-memory", Memory.KiloBytes)
    val unknownHistogram = histogram("unknown-histogram", UnitOfMeasurement.Unknown)
    val unknownCounter = counter("unknown-counter", UnitOfMeasurement.Unknown)
  }

  object ScaleDecoratorTestMetrics extends EntityRecorderFactory[ScaleDecoratorTestMetrics] {
    override def category: String = "decorator-spec"

    override def createRecorder(instrumentFactory: InstrumentFactory): ScaleDecoratorTestMetrics =
      new ScaleDecoratorTestMetrics(instrumentFactory)
  }

  val testEntity = Entity("metrics-scale-decorator-spec", "decorator-spec")
  val recorder = Kamon.metrics.entity(ScaleDecoratorTestMetrics, "metrics-scale-decorator-spec")

  val emptySnapshot = TickMetricSnapshot(new MilliTimestamp(1000), new MilliTimestamp(2000), Map.empty)

  recorder.unknownCounter.increment(10)
  recorder.unknownHistogram.record(1000L)
  recorder.nanoTime.record(10000000L)
  recorder.microTime.increment(1000000L)
  recorder.byteMemory.record(1024L)
  recorder.kbyteMemory.increment(100L)

  val nonEmptySnapshot = TickMetricSnapshot(new MilliTimestamp(1000), new MilliTimestamp(2000), Map(
    (testEntity -> recorder.collect(collectionContext))))

}

