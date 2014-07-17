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
import kamon.metrics.MemoryMetrics.MemoryMetricSnapshot
import kamon.system.SystemMetricsExtension
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._

class MemoryMetricsSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit lazy val system: ActorSystem = ActorSystem("memory-metrics-spec", ConfigFactory.parseString(
    """
      |akka {
      |  extensions = ["kamon.system.SystemMetrics"]
      |}
      |
      |kamon.metrics {
      |
      |  tick-interval = 1 second
      |
      |  system {
      |     memory {
      |        used {
      |          highest-trackable-value = 3600000000000
      |          significant-value-digits = 2
      |        }
      |        free {
      |          highest-trackable-value = 3600000000000
      |          significant-value-digits = 2
      |        }
      |        buffer {
      |          highest-trackable-value = 3600000000000
      |          significant-value-digits = 2
      |        }
      |        cache {
      |          highest-trackable-value = 3600000000000
      |          significant-value-digits = 2
      |        }
      |        swap-used {
      |          highest-trackable-value = 3600000000000
      |          significant-value-digits = 2
      |        }
      |        swap-free {
      |          highest-trackable-value = 3600000000000
      |          significant-value-digits = 2
      |        }
      |     }
      |  }
      |}
    """.stripMargin))

  "the Kamon Memory metrics" should {
    "record used, free, buffer, cache, swap used, swap free metrics" in new MetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val MemoryMetrics = expectMemoryMetrics(metricsListener, 3 seconds)
      MemoryMetrics.used.max should be >= 0L
      MemoryMetrics.free.max should be >= 0L
      MemoryMetrics.buffer.max should be >= 0L
      MemoryMetrics.cache.max should be >= 0L
      MemoryMetrics.swapUsed.max should be >= 0L
      MemoryMetrics.swapFree.max should be >= 0L
    }
  }

  def expectMemoryMetrics(listener: TestProbe, waitTime: FiniteDuration): MemoryMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val memoryMetricsOption = tickSnapshot.metrics.get(MemoryMetrics(SystemMetricsExtension.Memory))
    memoryMetricsOption should not be empty
    memoryMetricsOption.get.asInstanceOf[MemoryMetricSnapshot]
  }

  trait MetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(MemoryMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }
}
