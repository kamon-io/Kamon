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

import java.nio.LongBuffer

import akka.actor._
import akka.kamon.instrumentation.ActorCellMetrics
import akka.routing._
import akka.testkit.{ ImplicitSender, TestKitBase, TestProbe }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.akka.{ RouterMetrics, ActorMetrics }
import ActorMetrics.{ ActorMetricSnapshot, ActorMetricsRecorder }
import RouterMetrics._
import kamon.metric.RouterMetricsTestActor._
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metric.instrument.{ Counter, Histogram }
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

import scala.concurrent.duration._

class RouterMetricsSpec extends TestKitBase with WordSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {
  implicit lazy val system: ActorSystem = ActorSystem("router-metrics-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  tick-interval = 1 hour
      |  default-collection-context-buffer-size = 10
      |
      |  filters = [
      |    {
      |      router {
      |        includes = [ "user/tracked-*", "user/measuring-*", "user/stop-*" ]
      |        excludes = [ "user/tracked-explicitly-excluded-*"]
      |      }
      |    }
      |  ]
      |  precision {
      |    default-histogram-precision {
      |      highest-trackable-value = 3600000000000
      |      significant-value-digits = 2
      |    }
      |  }
      |}
      |
      |akka.loglevel = OFF
      |
    """.stripMargin))

  "the Kamon router metrics" should {
    "respect the configured include and exclude filters" in new RouterMetricsFixtures {
      createTestPoolRouter("tracked-pool-router")
      createTestGroupRouter("tracked-group-router")
      createTestPoolRouter("non-tracked-pool-router")
      createTestGroupRouter("non-tracked-group-router")
      createTestPoolRouter("tracked-explicitly-excluded-pool-router")
      createTestGroupRouter("tracked-explicitly-excluded-group-router")

      routerMetricsRecorderOf("user/tracked-pool-router") should not be empty
      routerMetricsRecorderOf("user/tracked-group-router") should not be empty
      routerMetricsRecorderOf("user/non-tracked-pool-router") shouldBe empty
      routerMetricsRecorderOf("user/non-tracked-group-router") shouldBe empty
      routerMetricsRecorderOf("user/tracked-explicitly-excluded-pool-router") shouldBe empty
      routerMetricsRecorderOf("user/tracked-explicitly-excluded-group-router") shouldBe empty
    }

    "record the routing-time of the receive function for pool routers" in new RouterMetricsFixtures {
      val listener = TestProbe()
      val router = createTestPoolRouter("measuring-routing-time-in-pool-router")

      router.tell(Ping, listener.ref)
      listener.expectMsg(Pong)
      val routerSnapshot = collectMetricsOf("user/measuring-routing-time-in-pool-router").get

      routerSnapshot.routingTime.numberOfMeasurements should be(1L)
    }

    "record the routing-time of the receive function for group routers" in new RouterMetricsFixtures {
      val listener = TestProbe()
      val router = createTestGroupRouter("measuring-routing-time-in-group-router")

      router.tell(Ping, listener.ref)
      listener.expectMsg(Pong)
      val routerSnapshot = collectMetricsOf("user/measuring-routing-time-in-group-router").get

      routerSnapshot.routingTime.numberOfMeasurements should be(1L)
    }

    "record the processing-time of the receive function for pool routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      val router = createTestPoolRouter("measuring-processing-time-in-pool-router")

      router.tell(RouterTrackTimings(sleep = Some(1 second)), timingsListener.ref)
      val timings = timingsListener.expectMsgType[RouterTrackedTimings]
      val routerSnapshot = collectMetricsOf("user/measuring-processing-time-in-pool-router").get

      routerSnapshot.processingTime.numberOfMeasurements should be(1L)
      routerSnapshot.processingTime.recordsIterator.next().count should be(1L)
      routerSnapshot.processingTime.recordsIterator.next().level should be(timings.approximateProcessingTime +- 10.millis.toNanos)
    }

    "record the processing-time of the receive function for group routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      val router = createTestGroupRouter("measuring-processing-time-in-group-router")

      router.tell(RouterTrackTimings(sleep = Some(1 second)), timingsListener.ref)
      val timings = timingsListener.expectMsgType[RouterTrackedTimings]
      val routerSnapshot = collectMetricsOf("user/measuring-processing-time-in-group-router").get

      routerSnapshot.processingTime.numberOfMeasurements should be(1L)
      routerSnapshot.processingTime.recordsIterator.next().count should be(1L)
      routerSnapshot.processingTime.recordsIterator.next().level should be(timings.approximateProcessingTime +- 10.millis.toNanos)
    }

    "record the number of errors for pool routers" in new RouterMetricsFixtures {
      val listener = TestProbe()
      val router = createTestPoolRouter("measuring-errors-in-pool-router")

      for (i ← 1 to 10) {
        router.tell(Fail, listener.ref)
      }

      router.tell(Ping, listener.ref)
      listener.expectMsg(Pong)

      val routerSnapshot = collectMetricsOf("user/measuring-errors-in-pool-router").get
      routerSnapshot.errors.count should be(10L)
    }

    "record the number of errors for group routers" in new RouterMetricsFixtures {
      val listener = TestProbe()
      val router = createTestGroupRouter("measuring-errors-in-group-router")

      for (i ← 1 to 10) {
        router.tell(Fail, listener.ref)
      }

      router.tell(Ping, listener.ref)
      listener.expectMsg(Pong)

      val routerSnapshot = collectMetricsOf("user/measuring-errors-in-group-router").get
      routerSnapshot.errors.count should be(10L)
    }

    "record the time-in-mailbox for pool routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      val router = createTestPoolRouter("measuring-time-in-mailbox-in-pool-router")

      router.tell(RouterTrackTimings(sleep = Some(1 second)), timingsListener.ref)
      val timings = timingsListener.expectMsgType[RouterTrackedTimings]
      val routerSnapshot = collectMetricsOf("user/measuring-time-in-mailbox-in-pool-router").get

      routerSnapshot.timeInMailbox.numberOfMeasurements should be(1L)
      routerSnapshot.timeInMailbox.recordsIterator.next().count should be(1L)
      routerSnapshot.timeInMailbox.recordsIterator.next().level should be(timings.approximateTimeInMailbox +- 10.millis.toNanos)
    }

    "record the time-in-mailbox for group routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      val router = createTestGroupRouter("measuring-time-in-mailbox-in-group-router")

      router.tell(RouterTrackTimings(sleep = Some(1 second)), timingsListener.ref)
      val timings = timingsListener.expectMsgType[RouterTrackedTimings]
      val routerSnapshot = collectMetricsOf("user/measuring-time-in-mailbox-in-group-router").get

      routerSnapshot.timeInMailbox.numberOfMeasurements should be(1L)
      routerSnapshot.timeInMailbox.recordsIterator.next().count should be(1L)
      routerSnapshot.timeInMailbox.recordsIterator.next().level should be(timings.approximateTimeInMailbox +- 10.millis.toNanos)
    }

    "clean up the associated recorder when the pool router is stopped" in new RouterMetricsFixtures {
      val trackedRouter = createTestPoolRouter("stop-in-pool-router")
      routerMetricsRecorderOf("user/stop-in-pool-router") should not be empty

      val deathWatcher = TestProbe()
      deathWatcher.watch(trackedRouter)
      trackedRouter ! PoisonPill
      deathWatcher.expectTerminated(trackedRouter)

      routerMetricsRecorderOf("user/stop-in-pool-router") shouldBe empty
    }

    "clean up the associated recorder when the group router is stopped" in new RouterMetricsFixtures {
      val trackedRouter = createTestPoolRouter("stop-in-group-router")
      routerMetricsRecorderOf("user/stop-in-group-router") should not be empty

      val deathWatcher = TestProbe()
      deathWatcher.watch(trackedRouter)
      trackedRouter ! PoisonPill
      deathWatcher.expectTerminated(trackedRouter)

      routerMetricsRecorderOf("user/stop-in-group-router") shouldBe empty
    }
  }

  override protected def afterAll(): Unit = shutdown()

  trait RouterMetricsFixtures {
    val collectionContext = new CollectionContext {
      val buffer: LongBuffer = LongBuffer.allocate(10000)
    }

    def routerMetricsRecorderOf(routerName: String): Option[RouterMetricsRecorder] =
      Kamon(Metrics)(system).storage.get(RouterMetrics(routerName)).map(_.asInstanceOf[RouterMetricsRecorder])

    def collectMetricsOf(routerName: String): Option[RouterMetricSnapshot] = {
      Thread.sleep(5) // Just in case the test advances a bit faster than the actor being tested.
      routerMetricsRecorderOf(routerName).map(_.collect(collectionContext))
    }

    def createTestGroupRouter(routerName: String): ActorRef = {
      val routees = Vector.fill(5) {
        system.actorOf(Props[RouterMetricsTestActor])
      }

      val group = system.actorOf(RoundRobinGroup(routees.map(_.path.toStringWithoutAddress)).props(), routerName)

      //val router = system.actorOf(RoundRobinPool(5).props(Props[RouterMetricsTestActor]), routerName)
      val initialiseListener = TestProbe()

      // Ensure that the router has been created before returning.
      group.tell(Ping, initialiseListener.ref)
      initialiseListener.expectMsg(Pong)

      // Cleanup all the metric recording instruments:
      collectMetricsOf("user/" + routerName)

      group
    }

    def createTestPoolRouter(routerName: String): ActorRef = {
      val router = system.actorOf(RoundRobinPool(5).props(Props[RouterMetricsTestActor]), routerName)
      val initialiseListener = TestProbe()

      // Ensure that the router has been created before returning.
      router.tell(Ping, initialiseListener.ref)
      initialiseListener.expectMsg(Pong)

      // Cleanup all the metric recording instruments:
      collectMetricsOf("user/" + routerName)

      router
    }
  }

  trait ActorMetricsFixtures {
    val collectionContext = new CollectionContext {
      val buffer: LongBuffer = LongBuffer.allocate(10000)
    }

    def createTestActor(name: String): ActorRef = system.actorOf(Props[ActorMetricsTestActor], name)

    def takeSnapshotOf(amr: ActorMetricsRecorder): ActorMetricSnapshot = amr.collect(collectionContext)
  }
}

class RouterMetricsTestActor extends Actor {
  def receive = {
    case Discard ⇒
    case Fail    ⇒ throw new ArithmeticException("Division by zero.")
    case Ping    ⇒ sender ! Pong
    case RouterTrackTimings(sendTimestamp, sleep) ⇒ {
      val dequeueTimestamp = System.nanoTime()
      sleep.map(s ⇒ Thread.sleep(s.toMillis))
      val afterReceiveTimestamp = System.nanoTime()

      sender ! RouterTrackedTimings(sendTimestamp, dequeueTimestamp, afterReceiveTimestamp)
    }
  }
}

object RouterMetricsTestActor {
  case object Ping
  case object Pong
  case object Fail
  case object Discard

  case class RouterTrackTimings(sendTimestamp: Long = System.nanoTime(), sleep: Option[Duration] = None)
  case class RouterTrackedTimings(sendTimestamp: Long, dequeueTimestamp: Long, afterReceiveTimestamp: Long) {
    def approximateTimeInMailbox: Long = dequeueTimestamp - sendTimestamp
    def approximateProcessingTime: Long = afterReceiveTimestamp - dequeueTimestamp
  }
}
