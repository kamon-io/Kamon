/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

import com.typesafe.config.ConfigFactory
import kamon.{ MilliTimestamp, Kamon }
import kamon.metric.instrument.Histogram
import kamon.metric.instrument.Histogram.MutableRecord
import org.scalatest.{ Matchers, WordSpecLike }
import akka.testkit.{ ImplicitSender, TestKitBase }
import akka.actor.ActorSystem
import scala.concurrent.duration._
import kamon.metric.Subscriptions.TickMetricSnapshot

class TickMetricSnapshotBufferSpec extends TestKitBase with WordSpecLike with Matchers with ImplicitSender {
  implicit lazy val system: ActorSystem = ActorSystem("trace-metrics-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  tick-interval = 1 hour
      |  default-collection-context-buffer-size = 10
      |
      |  filters = [
      |    {
      |      trace {
      |        includes = [ "*" ]
      |        excludes = [ "non-tracked-trace"]
      |      }
      |    }
      |  ]
      |}
    """.stripMargin))

  "the TickMetricSnapshotBuffer" should {
    "merge TickMetricSnapshots received until the flush timeout is reached and fix the from/to fields" in new SnapshotFixtures {
      val buffer = system.actorOf(TickMetricSnapshotBuffer.props(3 seconds, testActor))

      buffer ! firstEmpty
      buffer ! secondEmpty
      buffer ! thirdEmpty

      within(2 seconds)(expectNoMsg())
      val mergedSnapshot = expectMsgType[TickMetricSnapshot]

      mergedSnapshot.from.millis should equal(1000)
      mergedSnapshot.to.millis should equal(4000)
      mergedSnapshot.metrics should be('empty)
    }

    "merge empty and non-empty snapshots" in new SnapshotFixtures {
      val buffer = system.actorOf(TickMetricSnapshotBuffer.props(3 seconds, testActor))

      buffer ! firstNonEmpty
      buffer ! secondNonEmpty
      buffer ! thirdEmpty

      within(2 seconds)(expectNoMsg())
      val mergedSnapshot = expectMsgType[TickMetricSnapshot]

      mergedSnapshot.from.millis should equal(1000)
      mergedSnapshot.to.millis should equal(4000)
      mergedSnapshot.metrics should not be ('empty)

      val testMetricSnapshot = mergedSnapshot.metrics(testTraceIdentity).metrics(TraceMetrics.ElapsedTime).asInstanceOf[Histogram.Snapshot]
      testMetricSnapshot.min should equal(10)
      testMetricSnapshot.max should equal(300)
      testMetricSnapshot.numberOfMeasurements should equal(6)
      testMetricSnapshot.recordsIterator.toStream should contain allOf (
        MutableRecord(10, 3),
        MutableRecord(20, 1),
        MutableRecord(30, 1),
        MutableRecord(300, 1))

    }
  }

  trait SnapshotFixtures {
    val collectionContext = Kamon(Metrics).buildDefaultCollectionContext
    val testTraceIdentity = TraceMetrics("buffer-spec-test-trace")
    val traceRecorder = Kamon(Metrics).register(testTraceIdentity, TraceMetrics.Factory).get

    val firstEmpty = TickMetricSnapshot(new MilliTimestamp(1000), new MilliTimestamp(2000), Map.empty)
    val secondEmpty = TickMetricSnapshot(new MilliTimestamp(2000), new MilliTimestamp(3000), Map.empty)
    val thirdEmpty = TickMetricSnapshot(new MilliTimestamp(3000), new MilliTimestamp(4000), Map.empty)

    traceRecorder.elapsedTime.record(10L)
    traceRecorder.elapsedTime.record(20L)
    traceRecorder.elapsedTime.record(30L)
    val firstNonEmpty = TickMetricSnapshot(new MilliTimestamp(1000), new MilliTimestamp(2000), Map(
      (testTraceIdentity -> traceRecorder.collect(collectionContext))))

    traceRecorder.elapsedTime.record(10L)
    traceRecorder.elapsedTime.record(10L)
    traceRecorder.elapsedTime.record(300L)
    val secondNonEmpty = TickMetricSnapshot(new MilliTimestamp(1000), new MilliTimestamp(2000), Map(
      (testTraceIdentity -> traceRecorder.collect(collectionContext))))
  }
}
