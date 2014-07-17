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

package kamon.metric

import akka.actor.ActorSystem
import akka.testkit.{ TestKitBase, TestProbe }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metrics.CPUMetrics
import kamon.metrics.CPUMetrics.CPUMetricSnapshot
import kamon.system.SystemMetricsExtension
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._

class CPUMetricsSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit lazy val system: ActorSystem = ActorSystem("cpu-metrics-spec", ConfigFactory.parseString(
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
      |     cpu {
      |        user {
      |            highest-trackable-value = 999999999
      |            significant-value-digits = 2
      |        }
      |        system {
      |            highest-trackable-value = 999999999
      |            significant-value-digits = 2
      |        }
      |        wait {
      |            highest-trackable-value = 999999999
      |            significant-value-digits = 2
      |        }
      |        idle {
      |            highest-trackable-value = 999999999
      |            significant-value-digits = 2
      |        }
      |     }
      |  }
      |}
    """.stripMargin))

  "the Kamon CPU metrics" should {
    "record user, system, wait, idle metrics" in new MetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val CPUMetrics = expectCPUMetrics(metricsListener, 3 seconds)
      CPUMetrics.user.max should be > 0L
      CPUMetrics.system.max should be > 0L
      CPUMetrics.cpuWait.max should be > 0L
      CPUMetrics.idle.max should be > 0L
    }
  }

  def expectCPUMetrics(listener: TestProbe, waitTime: FiniteDuration): CPUMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val cpuMetricsOption = tickSnapshot.metrics.get(CPUMetrics(SystemMetricsExtension.CPU))
    cpuMetricsOption should not be empty
    cpuMetricsOption.get.asInstanceOf[CPUMetricSnapshot]
  }

  trait MetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(CPUMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }
}
