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
import kamon.akka.RouterMetricsTestActor.{Ping, Pong}
import kamon.executors.Metrics._
import kamon.testkit.BaseKamonSpec
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.concurrent.Eventually

import scala.concurrent.Future

class DispatcherMetricsSpec extends TestKit(ActorSystem("DispatcherMetricsSpec")) with WordSpecLike with BaseKamonSpec with Matchers
    with BeforeAndAfterAll with ImplicitSender with Eventually {

  "the Kamon dispatcher metrics" should {
    "track dispatchers configured in the akka.dispatcher filter" in {
      forceInit(system.dispatchers.lookup("akka.actor.default-dispatcher"))
      forceInit(system.dispatchers.lookup("tracked-fjp"))
      forceInit(system.dispatchers.lookup("tracked-tpe"))
      forceInit(system.dispatchers.lookup("explicitly-excluded"))

      forkJoinPoolParallelism.valuesForTag("name") should contain only("akka.actor.default-dispatcher", "tracked-fjp")
      threadPoolSize.valuesForTag("name") should contain only("tracked-tpe")
    }


    "clean up the metrics recorders after a dispatcher is shutdown" in {
      implicit val tpeDispatcher = system.dispatchers.lookup("tracked-tpe")
      implicit val fjpDispatcher = system.dispatchers.lookup("tracked-fjp")

      forkJoinPoolParallelism.valuesForTag("name") should contain("tracked-fjp")
      threadPoolSize.valuesForTag("name") should contain("tracked-tpe")

      shutdownDispatcher(tpeDispatcher)
      shutdownDispatcher(fjpDispatcher)

      forkJoinPoolParallelism.valuesForTag("name") shouldNot contain("tracked-fjp")
      threadPoolSize.valuesForTag("name") shouldNot contain("tracked-tpe")
    }

    "play nicely when dispatchers are looked up from a BalancingPool router" in {
      val balancingPoolRouter = system.actorOf(BalancingPool(5).props(Props[RouterMetricsTestActor]), "test-balancing-pool")
      balancingPoolRouter ! Ping
      expectMsg(Pong)

      forkJoinPoolParallelism.valuesForTag("name") should contain("BalancingPool-/test-balancing-pool")
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

  def submit(dispatcher: MessageDispatcher): Future[String] = Future {
    "hello"
  }(dispatcher)

  def shutdownDispatcher(dispatcher: MessageDispatcher): Unit = {
    val shutdownMethod = dispatcher.getClass.getDeclaredMethod("shutdown")
    shutdownMethod.setAccessible(true)
    shutdownMethod.invoke(dispatcher)
  }

  override protected def afterAll(): Unit = system.shutdown()
}
