/* =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

import java.nio.LongBuffer

import akka.actor._
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.akka.ActorMetricsTestActor._
import kamon.metric.EntitySnapshot
import kamon.metric.instrument.CollectionContext
import kamon.testkit.BaseKamonSpec

import scala.concurrent.duration._

class ActorMetricsSpec extends BaseKamonSpec("actor-metrics-spec") {

  "the Kamon actor metrics" should {
    "respect the configured include and exclude filters" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("tracked-actor")
      actorMetricsRecorderOf(trackedActor) should not be empty

      val nonTrackedActor = createTestActor("non-tracked-actor")
      actorMetricsRecorderOf(nonTrackedActor) shouldBe empty

      val trackedButExplicitlyExcluded = createTestActor("tracked-explicitly-excluded")
      actorMetricsRecorderOf(trackedButExplicitlyExcluded) shouldBe empty
    }

    "not pick up the root supervisor" in {
      Kamon.metrics.find("actor-metrics-spec/", ActorMetrics.category) shouldBe empty
    }

    "reset all recording instruments after taking a snapshot" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("clean-after-collect")

      for (_ ← 1 to 100) {
        for (i ← 1 to 100) {
          trackedActor ! Discard
        }
        trackedActor ! Fail
        trackedActor ! Ping
        expectMsg(Pong)

        val firstSnapshot = collectMetricsOf(trackedActor).get
        firstSnapshot.counter("errors").get.count should be(1L)
        firstSnapshot.minMaxCounter("mailbox-size").get.numberOfMeasurements should be > 0L
        firstSnapshot.histogram("processing-time").get.numberOfMeasurements should be(102L) // 102 examples
        firstSnapshot.histogram("time-in-mailbox").get.numberOfMeasurements should be(102L) // 102 examples

        val secondSnapshot = collectMetricsOf(trackedActor).get // Ensure that the recorders are clean
        secondSnapshot.counter("errors").get.count should be(0L)
        secondSnapshot.minMaxCounter("mailbox-size").get.numberOfMeasurements should be(3L) // min, max and current
        secondSnapshot.histogram("processing-time").get.numberOfMeasurements should be(0L)
        secondSnapshot.histogram("time-in-mailbox").get.numberOfMeasurements should be(0L)
      }
    }

    "record the processing-time of the receive function" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("measuring-processing-time")

      trackedActor ! TrackTimings(sleep = Some(100 millis))
      val timings = expectMsgType[TrackedTimings]
      val snapshot = collectMetricsOf(trackedActor).get

      snapshot.histogram("processing-time").get.numberOfMeasurements should be(1L)
      snapshot.histogram("processing-time").get.recordsIterator.next().count should be(1L)
      snapshot.histogram("processing-time").get.recordsIterator.next().level should be(timings.approximateProcessingTime +- 10.millis.toNanos)
    }

    "record the number of errors" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("measuring-errors")

      for (i ← 1 to 10) { trackedActor ! Fail }
      trackedActor ! Ping
      expectMsg(Pong)
      val snapshot = collectMetricsOf(trackedActor).get

      snapshot.counter("errors").get.count should be(10)
    }

    "record the mailbox-size" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("measuring-mailbox-size")

      trackedActor ! TrackTimings(sleep = Some(100 millis))
      for (i ← 1 to 10) {
        trackedActor ! Discard
      }
      trackedActor ! Ping

      val timings = expectMsgType[TrackedTimings]
      expectMsg(Pong)
      val snapshot = collectMetricsOf(trackedActor).get

      snapshot.minMaxCounter("mailbox-size").get.min should be(0L)
      snapshot.minMaxCounter("mailbox-size").get.max should be(11L +- 1L)
    }

    "record the time-in-mailbox" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("measuring-time-in-mailbox")

      trackedActor ! TrackTimings(sleep = Some(100 millis))
      val timings = expectMsgType[TrackedTimings]
      val snapshot = collectMetricsOf(trackedActor).get

      snapshot.histogram("time-in-mailbox").get.numberOfMeasurements should be(1L)
      snapshot.histogram("time-in-mailbox").get.recordsIterator.next().count should be(1L)
      snapshot.histogram("time-in-mailbox").get.recordsIterator.next().level should be(timings.approximateTimeInMailbox +- 10.millis.toNanos)
    }

    "clean up the associated recorder when the actor is stopped" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("stop")
      val firstRecorder = actorMetricsRecorderOf(trackedActor).get

      // Killing the actor should remove it's ActorMetrics and registering again bellow should create a new one.
      val deathWatcher = TestProbe()
      deathWatcher.watch(trackedActor)
      trackedActor ! PoisonPill
      deathWatcher.expectTerminated(trackedActor)

      actorMetricsRecorderOf(trackedActor) shouldBe empty
    }
  }

  override protected def afterAll(): Unit = shutdown()

  trait ActorMetricsFixtures {
    val collectionContext = new CollectionContext {
      val buffer: LongBuffer = LongBuffer.allocate(10000)
    }

    def actorRecorderName(ref: ActorRef): String = system.name + "/" + ref.path.elements.mkString("/")

    def actorMetricsRecorderOf(ref: ActorRef): Option[ActorMetrics] =
      Kamon.metrics.find(actorRecorderName(ref), ActorMetrics.category).map(_.asInstanceOf[ActorMetrics])

    def collectMetricsOf(ref: ActorRef): Option[EntitySnapshot] = {
      Thread.sleep(5) // Just in case the test advances a bit faster than the actor being tested.
      actorMetricsRecorderOf(ref).map(_.collect(collectionContext))
    }

    def createTestActor(name: String): ActorRef = {
      val actor = system.actorOf(Props[ActorMetricsTestActor], name)
      val initialiseListener = TestProbe()

      // Ensure that the router has been created before returning.
      actor.tell(Ping, initialiseListener.ref)
      initialiseListener.expectMsg(Pong)

      // Cleanup all the metric recording instruments:
      collectMetricsOf(actor)

      actor
    }
  }
}

class ActorMetricsTestActor extends Actor {
  def receive = {
    case Discard ⇒
    case Fail    ⇒ throw new ArithmeticException("Division by zero.")
    case Ping    ⇒ sender ! Pong
    case TrackTimings(sendTimestamp, sleep) ⇒ {
      val dequeueTimestamp = System.nanoTime()
      sleep.map(s ⇒ Thread.sleep(s.toMillis))
      val afterReceiveTimestamp = System.nanoTime()

      sender ! TrackedTimings(sendTimestamp, dequeueTimestamp, afterReceiveTimestamp)
    }
  }
}

object ActorMetricsTestActor {
  case object Ping
  case object Pong
  case object Fail
  case object Discard

  case class TrackTimings(sendTimestamp: Long = System.nanoTime(), sleep: Option[Duration] = None)
  case class TrackedTimings(sendTimestamp: Long, dequeueTimestamp: Long, afterReceiveTimestamp: Long) {
    def approximateTimeInMailbox: Long = dequeueTimestamp - sendTimestamp
    def approximateProcessingTime: Long = afterReceiveTimestamp - dequeueTimestamp
  }
}
