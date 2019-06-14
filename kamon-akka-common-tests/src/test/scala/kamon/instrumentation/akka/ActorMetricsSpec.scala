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

package kamon.instrumentation.akka

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import ActorMetricsTestActor._
import kamon.instrumentation.akka.AkkaMetrics._
import kamon.tag.TagSet
import kamon.testkit.{InstrumentInspection, MetricInspection}
import org.scalactic.TimesOnInt._
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._


class ActorMetricsSpec extends TestKit(ActorSystem("ActorMetricsSpec")) with WordSpecLike with MetricInspection.Syntax with InstrumentInspection.Syntax with Matchers
    with BeforeAndAfterAll with ImplicitSender with Eventually {

  val systemMetrics = AkkaMetrics.forSystem(system.name)

  "the Actor System metrics" should {
    "record dead letters" in {
      val doaActor = system.actorOf(Props[ActorMetricsTestActor], "doa")
      val deathWatcher = TestProbe()
      deathWatcher.watch(doaActor)
      doaActor ! PoisonPill
      deathWatcher.expectTerminated(doaActor)

      7 times { doaActor ! "deadonarrival" }

      eventually {
        systemMetrics.deadLetters.value(false).toInt should be(7)
      }
    }

    "record unhandled messages" in {
      val unhandled = system.actorOf(Props[ActorMetricsTestActor], "unhandled")

      10 times { unhandled ! "CantHandleStrings" }

      eventually {
        systemMetrics.unhandledMessages.value(false).toInt should be(10)
      }
    }

    "record active actor counts" in {
      systemMetrics.activeActors.distribution(true)

      8 times { system.actorOf(Props[ActorMetricsTestActor]) ! Discard }

      eventually {
        systemMetrics.activeActors.distribution(false).max.toInt should be > 0
      }
    }

    "record processed messages counts" in {
      systemMetrics.processedMessagesByTracked.value(true)
      systemMetrics.processedMessagesByNonTracked.value(true)
      systemMetrics.processedMessagesByNonTracked.value(false) should be(0)

      val tracked = system.actorOf(Props[ActorMetricsTestActor], "tracked-actor-counts")
      val nonTracked = system.actorOf(Props[ActorMetricsTestActor], "non-tracked-actor-counts")

      (1 to 10).foreach(_ => tracked ! Discard)
      (1 to 15).foreach(_ => nonTracked ! Discard)

      eventually(timeout(3 second)) {
        systemMetrics.processedMessagesByTracked.value(false) should be >= (10L)
        systemMetrics.processedMessagesByNonTracked.value(false) should be >= (15L)
      }
    }
  }



  "the Kamon actor metrics" should {

    "respect the configured include and exclude filters" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("tracked-actor")
      ActorProcessingTime.tagValues("path") should contain("ActorMetricsSpec/user/tracked-actor")

      val nonTrackedActor = createTestActor("non-tracked-actor")
      ActorProcessingTime.tagValues("path") shouldNot contain("ActorMetricsSpec/user/non-tracked-actor")

      val trackedButExplicitlyExcluded = createTestActor("tracked-explicitly-excluded")
      ActorProcessingTime.tagValues("path") shouldNot contain("ActorMetricsSpec/user/tracked-explicitly-excluded")
    }

    "not pick up the root supervisor" in {
      ActorProcessingTime.tagValues("path") shouldNot contain("ActorMetricsSpec/")
    }

    "record the processing-time of the receive function" in new ActorMetricsFixtures {
      createTestActor("measuring-processing-time", true) ! TrackTimings(sleep = Some(100 millis))

      val timings = expectMsgType[TrackedTimings]
      val processingTimeDistribution = ActorProcessingTime.
        withTags(actorTags("ActorMetricsSpec/user/measuring-processing-time")).distribution()

      processingTimeDistribution.count should be(1L)
      processingTimeDistribution.buckets.size should be(1L)
      processingTimeDistribution.buckets.head.value should be(timings.approximateProcessingTime +- 10.millis.toNanos)
    }

    "record the number of errors" in new ActorMetricsFixtures {
      val trackedActor = createTestActor("measuring-errors")
      10.times(trackedActor ! Fail)

      trackedActor ! Ping
      expectMsg(Pong)
      ActorErrors.withTags(actorTags("ActorMetricsSpec/user/measuring-errors")).value() should be(10)
    }

   "record the mailbox-size" in new ActorMetricsFixtures {
     val trackedActor = createTestActor("measuring-mailbox-size", true)
     trackedActor ! TrackTimings(sleep = Some(1 second))
     10.times(trackedActor ! Discard)
     trackedActor ! Ping

     val timings = expectMsgType[TrackedTimings]
     expectMsg(Pong)

     val mailboxSizeDistribution = ActorMailboxSize
       .withTags(actorTags("ActorMetricsSpec/user/measuring-mailbox-size")).distribution()

     mailboxSizeDistribution.min should be(0L +- 1L)
     mailboxSizeDistribution.max should be(11L +- 1L)
   }

   "record the time-in-mailbox" in new ActorMetricsFixtures {
     val trackedActor = createTestActor("measuring-time-in-mailbox", true)
     trackedActor ! TrackTimings(sleep = Some(100 millis))
     val timings = expectMsgType[TrackedTimings]

     val timeInMailboxDistribution = ActorTimeInMailbox
       .withTags(actorTags("ActorMetricsSpec/user/measuring-time-in-mailbox")).distribution()

     timeInMailboxDistribution.count should be(1L)
     timeInMailboxDistribution.buckets.head.frequency should be(1L)
     timeInMailboxDistribution.buckets.head.value should be(timings.approximateTimeInMailbox +- 10.millis.toNanos)
   }

   "clean up the associated recorder when the actor is stopped" in new ActorMetricsFixtures {
     val trackedActor = createTestActor("stop")

     // Killing the actor should remove it's ActorMetrics and registering again bellow should create a new one.
     val deathWatcher = TestProbe()
     deathWatcher.watch(trackedActor)
     trackedActor ! PoisonPill
     deathWatcher.expectTerminated(trackedActor)

     eventually(timeout(1 second)) {
       ActorProcessingTime.tagValues("path") shouldNot contain("ActorMetricsSpec/user/stop")
     }
   }
  }


  override protected def afterAll(): Unit = shutdown()

  def actorTags(path: String): TagSet =
    TagSet.from(
      Map(
        "path" -> path,
        "system" -> "ActorMetricsSpec",
        "dispatcher" -> "akka.actor.default-dispatcher",
        "class" -> "kamon.instrumentation.akka.ActorMetricsTestActor"
      )
    )

  trait ActorMetricsFixtures {

    def createTestActor(name: String, resetState: Boolean = false): ActorRef = {
      val actor = system.actorOf(Props[ActorMetricsTestActor], name)
      val initialiseListener = TestProbe()

      // Ensure that the router has been created before returning.
      actor.tell(Ping, initialiseListener.ref)
      initialiseListener.expectMsg(Pong)

      // Cleanup all the metric recording instruments:
      if(resetState) {
        val tags = actorTags(s"ActorMetricsSpec/user/$name")

        ActorTimeInMailbox.withTags(tags).distribution(resetState = true)
        ActorProcessingTime.withTags(tags).distribution(resetState = true)
        ActorMailboxSize.withTags(tags).distribution(resetState = true)
        ActorErrors.withTags(tags).value(resetState = true)
      }

      actor
    }
  }
}
