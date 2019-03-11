/* =========================================================================================
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

package kamon.akka


import akka.actor.{ActorSystem, Props}
import akka.dispatch.MessageDispatcher
import akka.routing.BalancingPool
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.akka.RouterMetricsTestActor.{Ping, Pong}
import kamon.executors.Metrics._
import kamon.testkit.MetricInspection
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.concurrent.Eventually
import kamon.executors.{Metrics => ExecutorMetrics}

import scala.concurrent.Future

class DispatcherMetricsSpec extends TestKit(ActorSystem("DispatcherMetricsSpec")) with WordSpecLike with MetricInspection.Syntax with Matchers
  with BeforeAndAfterAll with ImplicitSender with Eventually {

  import ExecutorMetrics._

  Kamon.reconfigure(
    ConfigFactory.parseString("kamon.metric.tick-interval=1s")
      .withFallback(Kamon.config())
  )

  "the Kamon dispatcher metrics" should {

    val trackedDispatchers = Seq(
      "akka.actor.default-dispatcher",
      "tracked-fjp",
      "tracked-tpe"
    )

    val excluded = "explicitly-excluded"
    val allDispatchers = trackedDispatchers :+ excluded

    "track dispatchers configured in the akka.dispatcher filter" in {
      allDispatchers.foreach(id => forceInit(system.dispatchers.lookup(id)))

      val threads = threadsMetric.tagValues("name")
      val pools = poolMetric.tagValues("name")
      val queues = queueMetric.tagValues("name")
      val tasks = tasksMetric.tagValues("name")

      trackedDispatchers.forall { dispatcher =>
        threads.contains(dispatcher) &&
        pools.contains(dispatcher) &&
        queues.contains(dispatcher) &&
        tasks.contains(dispatcher)
      } should be (true)

      Seq(threads, pools, queues, tasks).flatten should not contain excluded
    }


    "clean up the metrics recorders after a dispatcher is shutdown" in {
      poolMetric.tagValues("name") should contain("tracked-fjp")
      shutdownDispatcher(system.dispatchers.lookup("tracked-fjp"))
      Thread.sleep(2000)
      poolMetric.tagValues("name") shouldNot contain("tracked-fjp")
    }

    "play nicely when dispatchers are looked up from a BalancingPool router" in {
      val balancingPoolRouter = system.actorOf(BalancingPool(5).props(Props[RouterMetricsTestActor]), "test-balancing-pool")
      balancingPoolRouter ! Ping
      expectMsg(Pong)

      poolMetric.tagValues("name") should contain("BalancingPool-/test-balancing-pool")
    }
  }


  def forceInit(dispatcher: MessageDispatcher): MessageDispatcher = {
    val listener = TestProbe()
    Future {
      listener.ref ! "init done"
    }(dispatcher)
    listener.expectMsg("init done")

    dispatcher
  }

  def shutdownDispatcher(dispatcher: MessageDispatcher): Unit = {
    val shutdownMethod = dispatcher.getClass.getDeclaredMethod("shutdown")
    shutdownMethod.setAccessible(true)
    shutdownMethod.invoke(dispatcher)
  }

  override protected def afterAll(): Unit = system.terminate()
}
