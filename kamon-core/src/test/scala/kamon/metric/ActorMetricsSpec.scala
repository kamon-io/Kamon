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

package kamon.metric

import java.nio.LongBuffer

import akka.kamon.instrumentation.ActorCellMetrics
import kamon.Kamon
import kamon.metric.ActorMetricsTestActor._
import kamon.metric.instrument.Histogram.MutableRecord
import org.scalatest.{ WordSpecLike, Matchers }
import akka.testkit.{ ImplicitSender, TestProbe, TestKitBase }
import akka.actor._
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metric.ActorMetrics.{ ActorMetricsRecorder, ActorMetricSnapshot }

class ActorMetricsSpec extends TestKitBase with WordSpecLike with Matchers with ImplicitSender {
  implicit lazy val system: ActorSystem = ActorSystem("actor-metrics-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  tick-interval = 1 hour
      |  default-collection-context-buffer-size = 10
      |
      |  filters = [
      |    {
      |      actor {
      |        includes = [ "user/tracked-*", "user/measuring-*", "user/clean-after-collect", "user/stop" ]
      |        excludes = [ "user/tracked-explicitly-excluded"]
      |      }
      |    }
      |  ]
      |  precision.actor {
      |    processing-time {
      |      highest-trackable-value = 3600000000000
      |      significant-value-digits = 2
      |    }
      |
      |    time-in-mailbox {
      |      highest-trackable-value = 3600000000000
      |      significant-value-digits = 2
      |    }
      |
      |    mailbox-size {
      |      refresh-interval = 1 hour
      |      highest-trackable-value = 999999999
      |      significant-value-digits = 2
      |    }
      |  }
      |}
    """.stripMargin))

  "the Kamon actor metrics" should {
    "respect the configured include and exclude filters" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("tracked-actor")
      actorMetricsRecorderOf(trackedActor) should not be empty

      val nonTrackedActor = createTestActor("non-tracked-actor")
      actorMetricsRecorderOf(nonTrackedActor) shouldBe empty

      val trackedButExplicitlyExcluded = createTestActor("tracked-explicitly-excluded")
      actorMetricsRecorderOf(trackedButExplicitlyExcluded) shouldBe empty
    }

    "reset all recording instruments after taking a snapshot" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("clean-after-collect")
      val trackedActorMetrics = actorMetricsRecorderOf(trackedActor).get
      for (i ← 1 to 100) {
        trackedActor ! Discard
      }
      trackedActor ! Fail
      trackedActor ! TrackTimings(sleep = Some(1 second))
      expectMsgType[TrackedTimings]

      val firstSnapshot = takeSnapshotOf(trackedActorMetrics)
      firstSnapshot.errors.count should be(1L)
      firstSnapshot.mailboxSize.numberOfMeasurements should be > 0L
      firstSnapshot.processingTime.numberOfMeasurements should be(103L) // 102 examples + Initialize message
      firstSnapshot.timeInMailbox.numberOfMeasurements should be(103L) // 102 examples + Initialize message

      val secondSnapshot = takeSnapshotOf(trackedActorMetrics) // Ensure that the recorders are clean
      secondSnapshot.errors.count should be(0L)
      secondSnapshot.mailboxSize.numberOfMeasurements should be(3L) // min, max and current
      secondSnapshot.processingTime.numberOfMeasurements should be(0L)
      secondSnapshot.timeInMailbox.numberOfMeasurements should be(0L)
    }

    "record the processing-time of the receive function" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("measuring-processing-time")
      val trackedActorMetrics = actorMetricsRecorderOf(trackedActor).get
      takeSnapshotOf(trackedActorMetrics) // Ensure that the recorders are clean

      trackedActor ! TrackTimings(sleep = Some(1 second))
      val timings = expectMsgType[TrackedTimings]
      val snapshot = takeSnapshotOf(trackedActorMetrics)

      snapshot.processingTime.numberOfMeasurements should be(1L)
      snapshot.processingTime.recordsIterator.next().count should be(1L)
      snapshot.processingTime.recordsIterator.next().level should be(timings.approximateProcessingTime +- 10.millis.toNanos)
    }

    "record the number of errors" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("measuring-errors")
      val trackedActorMetrics = actorMetricsRecorderOf(trackedActor).get
      takeSnapshotOf(trackedActorMetrics) // Ensure that the recorders are clean

      for (i ← 1 to 10) { trackedActor ! Fail }
      trackedActor ! Ping
      expectMsg(Pong)
      val snapshot = takeSnapshotOf(trackedActorMetrics)

      snapshot.errors.count should be(10)
    }

    "record the mailbox-size" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("measuring-mailbox-size")
      val trackedActorMetrics = actorMetricsRecorderOf(trackedActor).get
      takeSnapshotOf(trackedActorMetrics) // Ensure that the recorders are clean

      trackedActor ! TrackTimings(sleep = Some(1 second))
      for (i ← 1 to 10) {
        trackedActor ! Discard
      }
      trackedActor ! Ping

      val timings = expectMsgType[TrackedTimings]
      expectMsg(Pong)
      val snapshot = takeSnapshotOf(trackedActorMetrics)

      snapshot.mailboxSize.min should be(0L)
      snapshot.mailboxSize.max should be(11L +- 1L)
    }

    "record the time-in-mailbox" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("measuring-time-in-mailbox")
      val trackedActorMetrics = actorMetricsRecorderOf(trackedActor).get
      takeSnapshotOf(trackedActorMetrics) // Ensure that the recorders are clean

      trackedActor ! TrackTimings(sleep = Some(1 second))
      val timings = expectMsgType[TrackedTimings]
      val snapshot = takeSnapshotOf(trackedActorMetrics)

      snapshot.timeInMailbox.numberOfMeasurements should be(1L)
      snapshot.timeInMailbox.recordsIterator.next().count should be(1L)
      snapshot.timeInMailbox.recordsIterator.next().level should be(timings.approximateTimeInMailbox +- 10.millis.toNanos)
    }

    "clean up the associated recorder when the actor is stopped" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("stop")
      actorMetricsRecorderOf(trackedActor).get // force the actor to be initialized
      Kamon(Metrics).storage.get(ActorMetrics("user/stop")) should not be empty

      val deathWatcher = TestProbe()
      deathWatcher.watch(trackedActor)
      trackedActor ! PoisonPill
      deathWatcher.expectTerminated(trackedActor)

      Kamon(Metrics).storage.get(ActorMetrics("user/stop")) shouldBe empty
    }
  }

  trait ActorMetricsFixtures {
    val collectionContext = new CollectionContext {
      val buffer: LongBuffer = LongBuffer.allocate(10000)
    }

    def actorMetricsRecorderOf(ref: ActorRef): Option[ActorMetricsRecorder] = {
      val initialisationListener = TestProbe()
      ref.tell(Ping, initialisationListener.ref)
      initialisationListener.expectMsg(Pong)

      val underlyingCellField = ref.getClass.getDeclaredMethod("underlying")
      val cell = underlyingCellField.invoke(ref).asInstanceOf[ActorCellMetrics]

      cell.actorMetricsRecorder
    }

    def createTestActor(name: String): ActorRef = system.actorOf(Props[ActorMetricsTestActor], name)

    def takeSnapshotOf(amr: ActorMetricsRecorder): ActorMetricSnapshot = amr.collect(collectionContext)
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
