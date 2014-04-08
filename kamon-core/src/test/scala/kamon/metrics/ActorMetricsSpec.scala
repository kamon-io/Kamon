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

package kamon.metrics

import org.scalatest.{ WordSpecLike, Matchers }
import akka.testkit.{ TestProbe, TestKitBase }
import akka.actor.{ ActorRef, Actor, Props, ActorSystem }
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import kamon.Kamon
import kamon.metrics.Subscriptions.TickMetricSnapshot
import kamon.metrics.ActorMetrics.ActorMetricSnapshot
import kamon.metrics.MetricSnapshot.Measurement

class ActorMetricsSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit lazy val system: ActorSystem = ActorSystem("actor-metrics-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  filters = [
      |    {
      |      actor {
      |        includes = [ "user/tracked-*" ]
      |        excludes = [ "user/tracked-explicitly-excluded"]
      |      }
      |    }
      |  ]
      |}
    """.stripMargin))

  "the Kamon actor metrics" should {
    "respect the configured include and exclude filters" in new DelayableActorFixture {
      val tracked = system.actorOf(Props[DelayableActor], "tracked-actor")
      val nonTracked = system.actorOf(Props[DelayableActor], "non-tracked-actor")
      val trackedExplicitlyExcluded = system.actorOf(Props[DelayableActor], "tracked-explicitly-excluded")

      Kamon(Metrics).subscribe(ActorMetrics, "*", testActor, permanently = true)
      expectMsgType[TickMetricSnapshot]

      tracked ! Discard
      nonTracked ! Discard
      trackedExplicitlyExcluded ! Discard

      within(2 seconds) {
        val tickSnapshot = expectMsgType[TickMetricSnapshot]
        tickSnapshot.metrics.keys should contain(ActorMetrics("user/tracked-actor"))
        tickSnapshot.metrics.keys should not contain (ActorMetrics("user/non-tracked-actor"))
        tickSnapshot.metrics.keys should not contain (ActorMetrics("user/tracked-explicitly-excluded"))
      }
    }

    "record mailbox-size, processing-time and time-in-mailbox metrics under regular conditions" in new DelayableActorFixture {
      val (delayable, metricsListener) = delayableActor("tracked-normal-conditions")

      for (_ ← 1 to 10) {
        delayable ! Discard
      }

      val actorMetrics = expectActorMetrics("user/tracked-normal-conditions", metricsListener, 3 seconds)
      // Mailbox size is measured twice, on queue and dequeue, plus the automatic last-value recording.
      actorMetrics.mailboxSize.numberOfMeasurements should be(21)
      actorMetrics.mailboxSize.max should be <= 10L

      actorMetrics.processingTime.numberOfMeasurements should be(10L)

      actorMetrics.timeInMailbox.numberOfMeasurements should be(10L)
    }

    "keep a correct mailbox-size even if the actor is blocked processing a message" in new DelayableActorFixture {
      val (delayable, metricsListener) = delayableActor("tracked-mailbox-size-queueing-up")

      delayable ! Delay(2500 milliseconds)
      for (_ ← 1 to 9) {
        delayable ! Discard
      }

      // let the first snapshot pass
      metricsListener.expectMsgType[TickMetricSnapshot]

      // process the tick in which the actor is stalled.
      val stalledTickMetrics = expectActorMetrics("user/tracked-mailbox-size-queueing-up", metricsListener, 2 seconds)
      stalledTickMetrics.mailboxSize.numberOfMeasurements should equal(1)
      stalledTickMetrics.mailboxSize.measurements should contain only (Measurement(9, 1)) // only the automatic last-value recording
      stalledTickMetrics.mailboxSize.max should equal(9)
      stalledTickMetrics.processingTime.numberOfMeasurements should be(0L)
      stalledTickMetrics.timeInMailbox.numberOfMeasurements should be(0L)

      // process the tick after the actor is unblocked.
      val afterStallTickMetrics = expectActorMetrics("user/tracked-mailbox-size-queueing-up", metricsListener, 2 seconds)
      afterStallTickMetrics.processingTime.numberOfMeasurements should be(10L)
      afterStallTickMetrics.timeInMailbox.numberOfMeasurements should be(10L)
      afterStallTickMetrics.processingTime.max should be(2500.milliseconds.toNanos +- 100.milliseconds.toNanos)
      afterStallTickMetrics.timeInMailbox.max should be(2500.milliseconds.toNanos +- 100.milliseconds.toNanos)
    }
  }

  def expectActorMetrics(actorPath: String, listener: TestProbe, waitTime: FiniteDuration): ActorMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val actorMetricsOption = tickSnapshot.metrics.get(ActorMetrics(actorPath))
    actorMetricsOption should not be empty
    actorMetricsOption.get.asInstanceOf[ActorMetricSnapshot]
  }

  trait DelayableActorFixture {
    def delayableActor(name: String): (ActorRef, TestProbe) = {
      val actor = system.actorOf(Props[DelayableActor], name)
      val metricsListener = TestProbe()

      Kamon(Metrics).subscribe(ActorMetrics, "user/" + name, metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]

      (actor, metricsListener)
    }
  }
}

class DelayableActor extends Actor {
  def receive = {
    case Delay(time) ⇒ Thread.sleep(time.toMillis)
    case Discard     ⇒
  }
}

case object Discard
case class Delay(time: FiniteDuration)
