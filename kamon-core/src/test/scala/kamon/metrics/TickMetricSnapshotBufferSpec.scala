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

package kamon.metrics

import org.scalatest.{ Matchers, WordSpecLike }
import akka.testkit.TestKit
import akka.actor.ActorSystem
import scala.concurrent.duration._
import kamon.metrics.Subscriptions.TickMetricSnapshot
import kamon.metrics.MetricSnapshot.Measurement

class TickMetricSnapshotBufferSpec extends TestKit(ActorSystem("tick-metric-snapshot-buffer")) with WordSpecLike with Matchers {

  "the TickMetricSnapshotBuffer" should {
    "merge TickMetricSnapshots received until the flush timeout is reached and fix the from/to fields" in new SnapshotFixtures {
      val buffer = system.actorOf(TickMetricSnapshotBuffer.props(3 seconds, testActor))

      buffer ! firstEmpty
      buffer ! secondEmpty
      buffer ! thirdEmpty

      within(2 seconds)(expectNoMsg())
      val mergedSnapshot = expectMsgType[TickMetricSnapshot]

      mergedSnapshot.from should equal(1000)
      mergedSnapshot.to should equal(4000)
      mergedSnapshot.metrics should be('empty)
    }

    "merge empty and non-empty snapshots" in new SnapshotFixtures {
      val buffer = system.actorOf(TickMetricSnapshotBuffer.props(3 seconds, testActor))

      buffer ! firstNonEmpty
      buffer ! secondNonEmpty
      buffer ! thirdEmpty

      within(2 seconds)(expectNoMsg())
      val mergedSnapshot = expectMsgType[TickMetricSnapshot]

      mergedSnapshot.from should equal(1000)
      mergedSnapshot.to should equal(4000)
      mergedSnapshot.metrics should not be ('empty)

      val testMetricSnapshot = mergedSnapshot.metrics(CustomMetric("test-metric")).metrics(CustomMetric.RecordedValues)
      testMetricSnapshot.min should equal(1)
      testMetricSnapshot.max should equal(10)
      testMetricSnapshot.numberOfMeasurements should equal(35)
      testMetricSnapshot.measurements should contain allOf (Measurement(1, 10), Measurement(4, 9), Measurement(10, 16))

    }
  }

  trait SnapshotFixtures {
    val firstEmpty = TickMetricSnapshot(1000, 2000, Map.empty)
    val secondEmpty = TickMetricSnapshot(2000, 3000, Map.empty)
    val thirdEmpty = TickMetricSnapshot(3000, 4000, Map.empty)

    val firstNonEmpty = TickMetricSnapshot(1000, 2000,
      Map((CustomMetric("test-metric") -> SimpleGroupSnapshot(Map(CustomMetric.RecordedValues -> MetricSnapshot(20, Scale.Unit, Vector(Measurement(1, 10), Measurement(10, 10))))))))

    val secondNonEmpty = TickMetricSnapshot(1000, 2000,
      Map((CustomMetric("test-metric") -> SimpleGroupSnapshot(Map(CustomMetric.RecordedValues -> MetricSnapshot(15, Scale.Unit, Vector(Measurement(4, 9), Measurement(10, 6))))))))

  }

  case class SimpleGroupSnapshot(metrics: Map[MetricIdentity, MetricSnapshotLike]) extends MetricGroupSnapshot
}
