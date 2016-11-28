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

import akka.actor.{ ActorRef, Props }
import akka.dispatch.MessageDispatcher
import akka.routing.BalancingPool
import akka.testkit.TestProbe
import kamon.Kamon
import kamon.akka.RouterMetricsTestActor.{ Ping, Pong }
import kamon.metric.{ EntityRecorder, EntitySnapshot }
import kamon.testkit.BaseKamonSpec
import kamon.util.executors.{ ForkJoinPoolMetrics, ThreadPoolExecutorMetrics }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

class DispatcherMetricsSpec extends BaseKamonSpec("dispatcher-metrics-spec") {
  "the Kamon dispatcher metrics" should {
    "respect the configured include and exclude filters" in {
      val defaultDispatcher = forceInit(system.dispatchers.lookup("akka.actor.default-dispatcher"))
      val fjpDispatcher = forceInit(system.dispatchers.lookup("tracked-fjp"))
      val tpeDispatcher = forceInit(system.dispatchers.lookup("tracked-tpe"))
      val excludedDispatcher = forceInit(system.dispatchers.lookup("explicitly-excluded"))

      findDispatcherRecorder(defaultDispatcher, "fork-join-pool") shouldNot be(empty)
      findDispatcherRecorder(fjpDispatcher, "fork-join-pool") shouldNot be(empty)
      findDispatcherRecorder(tpeDispatcher, "thread-pool-executor") shouldNot be(empty)
      findDispatcherRecorder(excludedDispatcher, "fork-join-pool") should be(empty)
    }

    "record metrics for a dispatcher with thread-pool-executor" in {
      implicit val tpeDispatcher = system.dispatchers.lookup("tracked-tpe")
      refreshDispatcherInstruments(tpeDispatcher, "thread-pool-executor")
      collectDispatcherMetrics(tpeDispatcher, "thread-pool-executor")

      Await.result({
        Future.sequence {
          for (_ ← 1 to 100) yield submit(tpeDispatcher)
        }
      }, 5 seconds)

      refreshDispatcherInstruments(tpeDispatcher, "thread-pool-executor")
      val snapshot = collectDispatcherMetrics(tpeDispatcher, "thread-pool-executor")

      snapshot.gauge("active-threads") should not be empty
      snapshot.gauge("pool-size").get.min should be >= 7L
      snapshot.gauge("pool-size").get.max should be <= 21L
      snapshot.gauge("max-pool-size").get.max should be(21)
      snapshot.gauge("core-pool-size").get.max should be(21)
      snapshot.gauge("processed-tasks").get.max should be(102L +- 5L)

      // The processed tasks should be reset to 0 if no more tasks are submitted.
      val secondSnapshot = collectDispatcherMetrics(tpeDispatcher, "thread-pool-executor")
      secondSnapshot.gauge("processed-tasks").get.max should be(0)
    }

    "record metrics for a dispatcher with fork-join-executor" in {
      implicit val fjpDispatcher = system.dispatchers.lookup("tracked-fjp")
      collectDispatcherMetrics(fjpDispatcher, "fork-join-pool")

      Await.result({
        Future.sequence {
          for (_ ← 1 to 100) yield submit(fjpDispatcher)
        }
      }, 5 seconds)

      refreshDispatcherInstruments(fjpDispatcher, "fork-join-pool")
      val snapshot = collectDispatcherMetrics(fjpDispatcher, "fork-join-pool")

      snapshot.minMaxCounter("parallelism").get.max should be(22)
      snapshot.gauge("pool-size").get.min should be >= 0L
      snapshot.gauge("pool-size").get.max should be <= 22L
      snapshot.gauge("active-threads").get.max should be >= 0L
      snapshot.gauge("running-threads").get.max should be >= 0L
      snapshot.gauge("queued-task-count").get.max should be(0)

    }

    "clean up the metrics recorders after a dispatcher is shutdown" in {
      implicit val tpeDispatcher = system.dispatchers.lookup("tracked-tpe")
      implicit val fjpDispatcher = system.dispatchers.lookup("tracked-fjp")

      findDispatcherRecorder(fjpDispatcher, "fork-join-pool") shouldNot be(empty)
      findDispatcherRecorder(tpeDispatcher, "thread-pool-executor") shouldNot be(empty)

      shutdownDispatcher(tpeDispatcher)
      shutdownDispatcher(fjpDispatcher)

      findDispatcherRecorder(fjpDispatcher, "fork-join-pool") should be(empty)
      findDispatcherRecorder(tpeDispatcher, "thread-pool-executor") should be(empty)
    }

    "play nicely when dispatchers are looked up from a BalancingPool router" in {
      val balancingPoolRouter = system.actorOf(BalancingPool(5).props(Props[RouterMetricsTestActor]), "test-balancing-pool")

      balancingPoolRouter ! Ping
      expectMsg(Pong)

      findDispatcherRecorder("BalancingPool-/test-balancing-pool", "fork-join-pool") shouldNot be(empty)
    }
  }

  def actorRecorderName(ref: ActorRef): String = ref.path.elements.mkString("/")

  def findDispatcherRecorder(dispatcher: MessageDispatcher, dispatcherType: String): Option[EntityRecorder] =
    Kamon.metrics.find(system.name + "/" + dispatcher.id, "akka-dispatcher", tags = Map("dispatcher-type" -> dispatcherType))

  def findDispatcherRecorder(dispatcherID: String, dispatcherType: String): Option[EntityRecorder] =
    Kamon.metrics.find(system.name + "/" + dispatcherID, "akka-dispatcher", tags = Map("dispatcher-type" -> dispatcherType))

  def collectDispatcherMetrics(dispatcher: MessageDispatcher, dispatcherType: String): EntitySnapshot =
    findDispatcherRecorder(dispatcher, dispatcherType).map(_.collect(collectionContext)).get

  def refreshDispatcherInstruments(dispatcher: MessageDispatcher, dispatcherType: String): Unit = {
    findDispatcherRecorder(dispatcher, dispatcherType) match {
      case Some(tpe: ThreadPoolExecutorMetrics) ⇒
        tpe.processedTasks.refreshValue()
        tpe.activeThreads.refreshValue()
        tpe.maxPoolSize.refreshValue()
        tpe.poolSize.refreshValue()
        tpe.corePoolSize.refreshValue()

      case Some(fjp: ForkJoinPoolMetrics) ⇒
        fjp.activeThreads.refreshValue()
        fjp.poolSize.refreshValue()
        fjp.queuedTaskCount.refreshValue()
        fjp.paralellism.refreshValues()
        fjp.runningThreads.refreshValue()

      case other ⇒
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

