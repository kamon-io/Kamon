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
import kamon.metrics.NetworkMetrics.NetworkMetricSnapshot
import kamon.system.SystemMetricsExtension
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._

class NetworkMetricsSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit lazy val system: ActorSystem = ActorSystem("network-metrics-spec", ConfigFactory.parseString(
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
      |     network {
      |        rx-bytes {
      |            highest-trackable-value = 3600000000000
      |            significant-value-digits = 2
      |        }
      |        tx-bytes {
      |            highest-trackable-value = 3600000000000
      |            significant-value-digits = 2
      |        }
      |        rx-errors {
      |            highest-trackable-value = 3600000000000
      |            significant-value-digits = 2
      |        }
      |        tx-errors {
      |            highest-trackable-value = 3600000000000
      |            significant-value-digits = 2
      |        }
      |     }
      |  }
      |}
    """.stripMargin))

  "the Kamon Network metrics" should {
    "record rxBytes, txBytes, rxErrors, txErrors metrics" in new MetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val NetworkMetrics = expectNetworkMetrics(metricsListener, 3 seconds)
      NetworkMetrics.rxBytes.max should be >= 0L
      NetworkMetrics.txBytes.max should be >= 0L
      NetworkMetrics.rxErrors.max should be >= 0L
      NetworkMetrics.txErrors.max should be >= 0L
    }
  }

  def expectNetworkMetrics(listener: TestProbe, waitTime: FiniteDuration): NetworkMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val networkMetricsOption = tickSnapshot.metrics.get(NetworkMetrics(SystemMetricsExtension.Network))
    networkMetricsOption should not be empty
    networkMetricsOption.get.asInstanceOf[NetworkMetricSnapshot]
  }

  trait MetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(NetworkMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }
}
