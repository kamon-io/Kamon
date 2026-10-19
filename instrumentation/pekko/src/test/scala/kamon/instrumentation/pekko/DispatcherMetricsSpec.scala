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

package kamon.instrumentation.pekko

import org.apache.pekko.Version
import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.dispatch.MessageDispatcher
import org.apache.pekko.routing.BalancingPool
import org.apache.pekko.testkit.{ImplicitSender, TestKit, TestProbe}
import kamon.instrumentation.pekko.RouterMetricsTestActor._
import kamon.instrumentation.executor.ExecutorMetrics
import kamon.tag.Lookups.plain
import kamon.testkit.MetricInspection
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.Executors
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class DispatcherMetricsSpec extends TestKit(ActorSystem("DispatcherMetricsSpec")) with AnyWordSpecLike with Matchers
    with MetricInspection.Syntax
    with BeforeAndAfterAll with ImplicitSender with Eventually {

  "the Kamon dispatcher metrics" should {

    val trackedDispatchers = Seq(
      "pekko.actor.default-dispatcher",
      "tracked-pinned-dispatcher",
      "tracked-fjp",
      "tracked-tpe",
      "pekko.actor.internal-dispatcher"
    )

    val excluded = "explicitly-excluded"
    val allDispatchers = trackedDispatchers :+ excluded
    val builtInDispatchers = Seq("pekko.actor.default-dispatcher", "pekko.actor.internal-dispatcher")

    "track dispatchers configured in the pekko.dispatcher filter" in {
      allDispatchers.foreach(id => forceInit(system.dispatchers.lookup(id)))

      val threads = ExecutorMetrics.ThreadsActive.tagValues("name")
      val queues = ExecutorMetrics.QueueSize.tagValues("name")
      val tasks = ExecutorMetrics.TasksCompleted.tagValues("name")

      trackedDispatchers.forall { dispatcherName =>
        threads.contains(dispatcherName) &&
        queues.contains(dispatcherName) &&
        tasks.contains(dispatcherName)
      } should be(true)

      Seq(threads, queues, tasks).flatten should not contain excluded
    }

    "include the actor system name in the executor tags" in {
      val instrumentExecutorsWithSystem = ExecutorMetrics.ThreadsActive.instruments().keys
        .filter(_.get(plain("pekko.system")) == system.name)
        .map(_.get(plain("name")))

      instrumentExecutorsWithSystem should contain only (trackedDispatchers: _*)
    }

    "clean up the metrics recorders after a dispatcher is shutdown" in {
      ExecutorMetrics.Parallelism.tagValues("name") should contain("tracked-fjp")
      shutdownDispatcher(system.dispatchers.lookup("tracked-fjp"))
      Thread.sleep(2000)
      ExecutorMetrics.Parallelism.tagValues("name") shouldNot contain("tracked-fjp")
    }

    "play nicely when dispatchers are looked up from a BalancingPool router" in {
      val balancingPoolRouter =
        system.actorOf(BalancingPool(5).props(Props[RouterMetricsTestActor]), "test-balancing-pool")
      balancingPoolRouter ! Ping
      expectMsg(Pong)

      ExecutorMetrics.Parallelism.tagValues("name") should contain("BalancingPool-/test-balancing-pool")
    }

    "pick up default execution contexts provided when creating an actor system" in {
      val dec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8))
      val system = ActorSystem(name = "with-default-ec", defaultExecutionContext = Some(dec))

      val instrumentExecutorsWithSystem = ExecutorMetrics.ThreadsActive.instruments().keys
        .filter(_.get(plain("pekko.system")) == system.name)
        .map(_.get(plain("name")))

      instrumentExecutorsWithSystem should contain only (builtInDispatchers: _*)
      Await.result(system.terminate(), 5 seconds)
    }

    "pick up default execution contexts provided when creating an actor system when the type is unknown" in {
      val dec = new WrappingExecutionContext(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8)))
      val system = ActorSystem(name = "with-unknown-default-ec", defaultExecutionContext = Some(dec))

      val instrumentExecutorsWithSystem = ExecutorMetrics.ThreadsActive.instruments().keys
        .filter(_.get(plain("pekko.system")) == system.name)
        .map(_.get(plain("name")))

      val builtInWithoutDefaultDispatcher = builtInDispatchers.filterNot(_.endsWith("default-dispatcher"))
      if (builtInWithoutDefaultDispatcher.isEmpty)
        instrumentExecutorsWithSystem shouldBe empty
      else
        instrumentExecutorsWithSystem should contain only (builtInWithoutDefaultDispatcher: _*)

      Await.result(system.terminate(), 5 seconds)
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

  class WrappingExecutionContext(ec: ExecutionContext) extends ExecutionContext {
    override def execute(runnable: Runnable): Unit = ec.execute(runnable)
    override def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)
  }
}
