/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import akka.actor.ActorSystem
import akka.testkit.{ TestKitBase, TestProbe }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.Metrics
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metrics.HeapMetrics.HeapMetricSnapshot
import kamon.system.SystemMetricsExtension
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._

class HeapMetricsSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit lazy val system: ActorSystem = ActorSystem("heap-metrics-spec", ConfigFactory.parseString(
    """
      |akka {
      |  extensions = ["kamon.system.SystemMetrics"]
      |}
      |
      |kamon.metrics {
      |
      |  tick-interval = 1 second
      |
      |  jvm {
      |     heap {
      |        used {
      |            highest-trackable-value = 3600000000000
      |            significant-value-digits = 2
      |        }
      |        max {
      |            highest-trackable-value = 3600000000000
      |            significant-value-digits = 2
      |        }
      |        committed {
      |            highest-trackable-value = 3600000000000
      |            significant-value-digits = 2
      |        }
      |     }
      |  }
      |}
    """.stripMargin))

  "the Kamon Heap metrics" should {
    "record used, max, commited metrics" in new MetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val HeapMetrics = expectHeapMetrics(metricsListener, 3 seconds)
      HeapMetrics.used.max should be >= 0L
      HeapMetrics.max.max should be >= 0L
      HeapMetrics.committed.max should be >= 0L
    }
  }

  def expectHeapMetrics(listener: TestProbe, waitTime: FiniteDuration): HeapMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val heapMetricsOption = tickSnapshot.metrics.get(HeapMetrics(SystemMetricsExtension.Heap))
    heapMetricsOption should not be empty
    heapMetricsOption.get.asInstanceOf[HeapMetricSnapshot]
  }

  trait MetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(HeapMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }
}
