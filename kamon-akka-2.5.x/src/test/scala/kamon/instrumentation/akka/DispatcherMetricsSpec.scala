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

package kamon.instrumentation.akka

import akka.actor.{ActorSystem, Props}
import akka.dispatch.MessageDispatcher
import akka.routing.BalancingPool
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import kamon.instrumentation.executor.ExecutorMetrics
import kamon.testkit.MetricInspection
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import RouterMetricsTestActor._
import scala.concurrent.Future

class DispatcherMetricsSpec extends TestKit(ActorSystem("DispatcherMetricsSpec")) with WordSpecLike with MetricInspection.Syntax with Matchers
  with BeforeAndAfterAll with ImplicitSender with Eventually {

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

      val threads = ExecutorMetrics.Threads.tagValues("name")
      val pools = ExecutorMetrics.Settings.tagValues("name")
      val queues = ExecutorMetrics.QueueSize.tagValues("name")
      val tasks = ExecutorMetrics.Tasks.tagValues("name")

      trackedDispatchers.forall { dispatcher =>
        threads.contains(dispatcher) &&
        pools.contains(dispatcher) &&
        queues.contains(dispatcher) &&
        tasks.contains(dispatcher)
      } should be (true)

      Seq(threads, pools, queues, tasks).flatten should not contain excluded
    }


    "clean up the metrics recorders after a dispatcher is shutdown" in {
      ExecutorMetrics.Settings.tagValues("name") should contain("tracked-fjp")
      shutdownDispatcher(system.dispatchers.lookup("tracked-fjp"))
      Thread.sleep(2000)
      ExecutorMetrics.Settings.tagValues("name") shouldNot contain("tracked-fjp")
    }

    "play nicely when dispatchers are looked up from a BalancingPool router" in {
      val balancingPoolRouter = system.actorOf(BalancingPool(5).props(Props[RouterMetricsTestActor]), "test-balancing-pool")
      balancingPoolRouter ! Ping
      expectMsg(Pong)

      ExecutorMetrics.Settings.tagValues("name") should contain("BalancingPool-/test-balancing-pool")
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
