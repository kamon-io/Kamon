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

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.testkit.{ TestKitBase, TestProbe }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.akka.DispatcherMetrics
import DispatcherMetrics.DispatcherMetricSnapshot
import kamon.metric.Subscriptions.TickMetricSnapshot
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._

class DispatcherMetricsSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit lazy val system: ActorSystem = ActorSystem("dispatcher-metrics-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  tick-interval = 1 second
      |  default-collection-context-buffer-size = 10
      |
      |  filters = [
      |    {
      |      dispatcher {
      |        includes = ["*"]
      |        excludes = ["dispatcher-explicitly-excluded"]
      |      }
      |    }
      |  ]
      |}
      |
      |dispatcher-explicitly-excluded {
      |   type = "Dispatcher"
      |   executor = "fork-join-executor"
      |}
      |
      |tracked-dispatcher {
      |   type = "Dispatcher"
      |   executor = "thread-pool-executor"
      |}
      |
    """.stripMargin))

  "the Kamon dispatcher metrics" should {
    "respect the configured include and exclude filters" in {
      system.actorOf(Props[ActorMetricsTestActor].withDispatcher("tracked-dispatcher"), "actor-with-tracked-dispatcher")
      system.actorOf(Props[ActorMetricsTestActor].withDispatcher("dispatcher-explicitly-excluded"), "actor-with-excluded-dispatcher")

      Kamon(Metrics).subscribe(DispatcherMetrics, "*", testActor, permanently = true)
      expectMsgType[TickMetricSnapshot]

      within(2 seconds) {
        val tickSnapshot = expectMsgType[TickMetricSnapshot]
        tickSnapshot.metrics.keys should contain(DispatcherMetrics("tracked-dispatcher"))
        tickSnapshot.metrics.keys should not contain (DispatcherMetrics("dispatcher-explicitly-excluded"))
      }
    }

    "record maximumPoolSize, runningThreadCount, queueTaskCount, poolSize metrics" in new DelayableActorFixture {
      val (delayable, metricsListener) = delayableActor("worker-actor", "tracked-dispatcher")

      for (_ ← 1 to 100) {
        //delayable ! Discard
      }

      val dispatcherMetrics = expectDispatcherMetrics("tracked-dispatcher", metricsListener, 3 seconds)
      dispatcherMetrics.maximumPoolSize.max should be <= 64L //fail in travis
      dispatcherMetrics.poolSize.max should be <= 22L //fail in travis
      dispatcherMetrics.queueTaskCount.max should be(0L)
      dispatcherMetrics.runningThreadCount.max should be(0L)
    }

  }

  def expectDispatcherMetrics(dispatcherId: String, listener: TestProbe, waitTime: FiniteDuration): DispatcherMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val dispatcherMetricsOption = tickSnapshot.metrics.get(DispatcherMetrics(dispatcherId))
    dispatcherMetricsOption should not be empty
    dispatcherMetricsOption.get.asInstanceOf[DispatcherMetricSnapshot]
  }

  trait DelayableActorFixture {
    def delayableActor(name: String, dispatcher: String): (ActorRef, TestProbe) = {
      val actor = system.actorOf(Props[ActorMetricsTestActor].withDispatcher(dispatcher), name)
      val metricsListener = TestProbe()

      Kamon(Metrics).subscribe(DispatcherMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]

      (actor, metricsListener)
    }
  }
}
