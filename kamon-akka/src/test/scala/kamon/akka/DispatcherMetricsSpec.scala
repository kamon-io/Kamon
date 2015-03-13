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

package kamon.akka

import akka.actor.ActorRef
import akka.dispatch.MessageDispatcher
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.{ EntityRecorder, EntitySnapshot }
import kamon.testkit.BaseKamonSpec

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

class DispatcherMetricsSpec extends BaseKamonSpec("dispatcher-metrics-spec") {
  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon.metric {
        |  tick-interval = 1 hour
        |  default-collection-context-buffer-size = 10
        |
        |  filters = {
        |    akka-dispatcher {
        |      includes = [ "**" ]
        |      excludes = [ "*/explicitly-excluded" ]
        |    }
        |  }
        |
        |  default-instrument-settings {
        |    gauge.refresh-interval = 1 hour
        |    min-max-counter.refresh-interval = 1 hour
        |  }
        |}
        |
        |explicitly-excluded {
        |  type = "Dispatcher"
        |  executor = "fork-join-executor"
        |}
        |
        |tracked-fjp {
        |  type = "Dispatcher"
        |  executor = "fork-join-executor"
        |
        |  fork-join-executor {
        |    parallelism-min = 8
        |    parallelism-factor = 100.0
        |    parallelism-max = 22
        |  }
        |}
        |
        |tracked-tpe {
        |  type = "Dispatcher"
        |  executor = "thread-pool-executor"
        |
        |  thread-pool-executor {
        |    core-pool-size-min = 7
        |    core-pool-size-factor = 100.0
        |    max-pool-size-factor  = 100.0
        |    max-pool-size-max = 21
        |  }
        |}
        |
      """.stripMargin)

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

    "clean up the metrics recorders after a dispatcher is shut down" in {
      implicit val tpeDispatcher = system.dispatchers.lookup("tracked-tpe")
      implicit val fjpDispatcher = system.dispatchers.lookup("tracked-fjp")

      findDispatcherRecorder(fjpDispatcher, "fork-join-pool") shouldNot be(empty)
      findDispatcherRecorder(tpeDispatcher, "thread-pool-executor") shouldNot be(empty)

      shutdownDispatcher(tpeDispatcher)
      shutdownDispatcher(fjpDispatcher)

      findDispatcherRecorder(fjpDispatcher, "fork-join-pool") should be(empty)
      findDispatcherRecorder(tpeDispatcher, "thread-pool-executor") should be(empty)
    }

  }

  def actorRecorderName(ref: ActorRef): String = ref.path.elements.mkString("/")

  def findDispatcherRecorder(dispatcher: MessageDispatcher, dispatcherType: String): Option[EntityRecorder] =
    Kamon.metrics.find(system.name + "/" + dispatcher.id, "akka-dispatcher", tags = Map("dispatcher-type" -> dispatcherType))

  def collectDispatcherMetrics(dispatcher: MessageDispatcher, dispatcherType: String): EntitySnapshot =
    findDispatcherRecorder(dispatcher, dispatcherType).map(_.collect(collectionContext)).get

  def refreshDispatcherInstruments(dispatcher: MessageDispatcher, dispatcherType: String): Unit = {
    findDispatcherRecorder(dispatcher, dispatcherType) match {
      case Some(tpe: ThreadPoolExecutorDispatcherMetrics) ⇒
        tpe.processedTasks.refreshValue()
        tpe.activeThreads.refreshValue()
        tpe.maxPoolSize.refreshValue()
        tpe.poolSize.refreshValue()
        tpe.corePoolSize.refreshValue()

      case Some(fjp: ForkJoinPoolDispatcherMetrics) ⇒
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

