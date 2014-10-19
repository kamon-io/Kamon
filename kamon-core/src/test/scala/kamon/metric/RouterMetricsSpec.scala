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
import akka.routing.RoundRobinPool
import akka.testkit.{ TestProbe, ImplicitSender, TestKitBase }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.RouterMetrics._
import kamon.metric.RouterMetricsTestActor._
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metric.instrument.{ Counter, Histogram }
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._

class RouterMetricsSpec extends TestKitBase with WordSpecLike with Matchers with ImplicitSender {
  implicit lazy val system: ActorSystem = ActorSystem("router-metrics-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  tick-interval = 1 second
      |  default-collection-context-buffer-size = 10
      |
      |  filters = [
      |    {
      |      router {
      |        includes = [ "user/tracked-*", "user/measuring-*", "user/stop" ]
      |        excludes = [ "user/tracked-explicitly-excluded"]
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
    """.stripMargin))

  "the Kamon router metrics" should {
    "respect the configured include and exclude filters" in new RouterMetricsFixtures {
      createTestRouter("tracked-router")
      createTestRouter("non-tracked-router")
      createTestRouter("tracked-explicitly-excluded")

      Kamon(Metrics).subscribe(RouterMetrics, "*", testActor, permanently = true)
      expectMsgType[TickMetricSnapshot]

      within(2 seconds) {
        val tickSnapshot = expectMsgType[TickMetricSnapshot]
        tickSnapshot.metrics.keys should contain(RouterMetrics("user/tracked-router"))
        tickSnapshot.metrics.keys should not contain (RouterMetrics("user/non-tracked-router"))
        tickSnapshot.metrics.keys should not contain (RouterMetrics("user/tracked-explicitly-excluded"))
      }
    }

    "record the processing-time of the receive function" in new RouterMetricsFixtures {
      val metricsListener = TestProbe()
      val trackedRouter = createTestRouter("measuring-processing-time")

      trackedRouter.tell(RouterTrackTimings(sleep = Some(1 second)), metricsListener.ref)
      val timings = metricsListener.expectMsgType[RouterTrackedTimings]

      val tickSnapshot = expectMsgType[TickMetricSnapshot].metrics
      tickSnapshot(RouterMetrics("user/measuring-processing-time")).metrics(ProcessingTime).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(1L)
      tickSnapshot(RouterMetrics("user/measuring-processing-time")).metrics(ProcessingTime).asInstanceOf[Histogram.Snapshot].recordsIterator.next().count should be(1L)
      // tickSnapshot(RouterMetrics("user/measuring-processing-time")).metrics(ProcessingTime).asInstanceOf[Histogram.Snapshot].recordsIterator.next().level should be(timings.approximateProcessingTime +- 10.millis.toNanos)
    }

    "record the number of errors" in new RouterMetricsFixtures {
      val metricsListener = TestProbe()
      val trackedRouter = createTestRouter("measuring-errors")

      for (i ← 1 to 10) {
        trackedRouter.tell(Fail, metricsListener.ref)
      }
      val tickSnapshot = expectMsgType[TickMetricSnapshot].metrics
      tickSnapshot(RouterMetrics("user/measuring-errors")).metrics(Errors).asInstanceOf[Counter.Snapshot].count should be(10L)
    }

    "record the time-in-mailbox" in new RouterMetricsFixtures {
      val metricsListener = TestProbe()
      val trackedRouter = createTestRouter("measuring-time-in-mailbox")

      trackedRouter.tell(RouterTrackTimings(sleep = Some(1 second)), metricsListener.ref)
      val timings = metricsListener.expectMsgType[RouterTrackedTimings]
      val tickSnapshot = expectMsgType[TickMetricSnapshot].metrics

      tickSnapshot(RouterMetrics("user/measuring-time-in-mailbox")).metrics(TimeInMailbox).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(1L)
      tickSnapshot(RouterMetrics("user/measuring-time-in-mailbox")).metrics(TimeInMailbox).asInstanceOf[Histogram.Snapshot].recordsIterator.next().count should be(1L)
      tickSnapshot(RouterMetrics("user/measuring-time-in-mailbox")).metrics(TimeInMailbox).asInstanceOf[Histogram.Snapshot].recordsIterator.next().level should be(timings.approximateTimeInMailbox +- 10.millis.toNanos)
    }

    "clean up the associated recorder when the actor is stopped" in new RouterMetricsFixtures {
      val trackedRouter = createTestRouter("stop")
      trackedRouter ! Ping
      Kamon(Metrics).storage.toString() // force to be initialized
      Kamon(Metrics).storage.get(RouterMetrics("user/stop")) should not be empty

      val deathWatcher = TestProbe()
      deathWatcher.watch(trackedRouter)
      trackedRouter ! PoisonPill
      deathWatcher.expectTerminated(trackedRouter)

      Kamon(Metrics).storage.get(RouterMetrics("user/stop")) shouldBe empty
    }
  }

  trait RouterMetricsFixtures {
    val collectionContext = new CollectionContext {
      val buffer: LongBuffer = LongBuffer.allocate(10000)
    }

    def createTestRouter(name: String): ActorRef = system.actorOf(RoundRobinPool(5).props(Props[RouterMetricsTestActor]), name)
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
