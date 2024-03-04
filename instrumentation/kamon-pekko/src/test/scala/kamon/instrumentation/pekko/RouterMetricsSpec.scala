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

package kamon.instrumentation.pekko

import org.apache.pekko.actor._
import org.apache.pekko.routing._
import org.apache.pekko.testkit.{ImplicitSender, TestKit, TestProbe}
import kamon.instrumentation.pekko.PekkoMetrics._
import kamon.instrumentation.pekko.RouterMetricsTestActor._
import kamon.tag.Lookups._
import kamon.tag.TagSet
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, MetricInspection}
import org.scalactic.TimesOnInt._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class RouterMetricsSpec extends TestKit(ActorSystem("RouterMetricsSpec")) with AnyWordSpecLike
    with MetricInspection.Syntax
    with InstrumentInspection.Syntax with Matchers with InitAndStopKamonAfterAll with ImplicitSender with Eventually {

  "the Kamon router metrics" should {
    "respect the configured include and exclude filters" in new RouterMetricsFixtures {
      createTestPoolRouter("tracked-pool-router")
      createTestPoolRouter("non-tracked-pool-router")
      createTestPoolRouter("tracked-explicitly-excluded-pool-router")

      RouterProcessingTime.tagValues("path") should contain("RouterMetricsSpec/user/tracked-pool-router")
      RouterProcessingTime.tagValues("path") shouldNot contain("RouterMetricsSpec/user/non-tracked-pool-router")
      RouterProcessingTime.tagValues("path") shouldNot contain(
        "RouterMetricsSpec/user/tracked-explicitly-excluded-pool-router"
      )
    }

    "record the routing-time of the receive function for pool routers" in new RouterMetricsFixtures {
      val listener = TestProbe()
      val router = createTestPoolRouter("measuring-routing-time-in-pool-router", true)

      router.tell(Ping, listener.ref)
      listener.expectMsg(Pong)

      eventually {
        RouterRoutingTime.withTags(routerTags("RouterMetricsSpec/user/measuring-routing-time-in-pool-router"))
          .distribution(resetState = false).count should be(1L)
      }
    }

    "record the processing-time of the receive function for pool routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      val router = createTestPoolRouter("measuring-processing-time-in-pool-router", true)

      router.tell(RouterTrackTimings(sleep = Some(1 second)), timingsListener.ref)
      val timings = timingsListener.expectMsgType[RouterTrackedTimings]
      val processingTimeDistribution = RouterProcessingTime
        .withTags(routerTags("RouterMetricsSpec/user/measuring-processing-time-in-pool-router")).distribution()

      processingTimeDistribution.count should be(1L)
      processingTimeDistribution.buckets.head.frequency should be(1L)
      processingTimeDistribution.buckets.head.value should be(timings.approximateProcessingTime +- 10.millis.toNanos)
    }

    "record the number of errors for pool routers" in new RouterMetricsFixtures {
      val listener = TestProbe()
      val router = createTestPoolRouter("measuring-errors-in-pool-router")

      10.times(router.tell(Fail, listener.ref))

      router.tell(Ping, listener.ref)
      listener.expectMsg(Pong)

      eventually {
        RouterErrors
          .withTags(routerTags("RouterMetricsSpec/user/measuring-errors-in-pool-router")).value(resetState =
            false
          ) should be(10L)
      }
    }

    "record the time-in-mailbox for pool routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      // If we don't initialize the listener upfront the timings might be wrong.
      timingsListener.testActor.tell("hello", timingsListener.ref)
      timingsListener.expectMsg("hello")
      val router = createTestPoolRouter("measuring-time-in-mailbox-in-pool-router", true)

      router.tell(RouterTrackTimings(sleep = Some(1 second)), timingsListener.ref)
      val timings = timingsListener.expectMsgType[RouterTrackedTimings]

      val timeInMailboxDistribution = RouterTimeInMailbox
        .withTags(routerTags("RouterMetricsSpec/user/measuring-time-in-mailbox-in-pool-router")).distribution()

      timeInMailboxDistribution.count should be(1L)
      timeInMailboxDistribution.buckets.head.frequency should be(1L)
      timeInMailboxDistribution.buckets.head.value should be(timings.approximateTimeInMailbox +- 10.millis.toNanos)
    }

    "record the time-in-mailbox for balancing pool routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      // If we don't initialize the listener upfront the timings might be wrong.
      timingsListener.testActor.tell("hello", timingsListener.ref)
      timingsListener.expectMsg("hello")
      val router = createTestBalancingPoolRouter("measuring-time-in-mailbox-in-balancing-pool-router", true)

      router.tell(RouterTrackTimings(sleep = Some(1 second)), timingsListener.ref)
      val timings = timingsListener.expectMsgType[RouterTrackedTimings]

      val timeInMailboxDistribution = RouterTimeInMailbox
        .withTags(
          routerTags("RouterMetricsSpec/user/measuring-time-in-mailbox-in-balancing-pool-router").withTags(
            TagSet.from(Map("dispatcher" -> "BalancingPool-/measuring-time-in-mailbox-in-balancing-pool-router"))
          )
        ).distribution()

      timeInMailboxDistribution.count should be(1L)
      timeInMailboxDistribution.buckets.head.frequency should be(1L)
      timeInMailboxDistribution.buckets.head.value should be(timings.approximateTimeInMailbox +- 10.millis.toNanos)
    }

    "record pending-messages for pool routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      val router = createTestPoolRouter("measuring-pending-messages-in-pool-router", true)
      def pendingMessagesDistribution = RouterPendingMessages
        .withTags(routerTags("RouterMetricsSpec/user/measuring-pending-messages-in-pool-router")).distribution()

      10 times { router.tell(RouterTrackTimings(sleep = Some(1 second)), timingsListener.ref) }
      10 times { timingsListener.expectMsgType[RouterTrackedTimings] }

      eventually(pendingMessagesDistribution.max should be >= (5L))
      eventually(pendingMessagesDistribution.max should be(0L))
    }

    "record pending-messages for balancing pool routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      val router = createTestBalancingPoolRouter("measuring-pending-messages-in-balancing-pool-router", true)
      def pendingMessagesDistribution = RouterPendingMessages
        .withTags(
          routerTags("RouterMetricsSpec/user/measuring-pending-messages-in-balancing-pool-router")
            .withTag("dispatcher", "BalancingPool-/measuring-pending-messages-in-balancing-pool-router")
        ).distribution()

      10 times { router.tell(RouterTrackTimings(sleep = Some(1 second)), timingsListener.ref) }
      10 times { timingsListener.expectMsgType[RouterTrackedTimings] }

      eventually(pendingMessagesDistribution.max should be >= (5L))
      eventually(pendingMessagesDistribution.max should be(0L))
    }

    "record member count for pool routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      val router = createTestPoolRouter("measuring-members-in-pool-router", true)
      def membersDistribution = RouterMembers
        .withTags(routerTags("RouterMetricsSpec/user/measuring-members-in-pool-router")).distribution()

      for (routeesLeft <- 4 to 0 by -1) {
        100 times { router.tell(Discard, timingsListener.ref) }
        router.tell(Die, timingsListener.ref)

        eventually {
          membersDistribution.max should be(routeesLeft)
        }
      }
    }

    "record member count for balancing pool routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      val router = createTestBalancingPoolRouter("measuring-members-in-balancing-pool-router", true)
      def membersDistribution = RouterMembers
        .withTags(
          routerTags("RouterMetricsSpec/user/measuring-members-in-balancing-pool-router")
            .withTags(TagSet.from(Map("dispatcher" -> "BalancingPool-/measuring-members-in-balancing-pool-router")))
        ).distribution()

      for (routeesLeft <- 4 to 0 by -1) {
        100 times { router.tell(Discard, timingsListener.ref) }
        router.tell(Die, timingsListener.ref)

        eventually {
          membersDistribution.max should be(routeesLeft)
        }
      }
    }

    "pick the right dispatcher name when the routees have a custom dispatcher set via deployment configuration" in new RouterMetricsFixtures {
      val testProbe = TestProbe()
      val router =
        system.actorOf(FromConfig.props(Props[RouterMetricsTestActor]), "picking-the-right-dispatcher-in-pool-router")

      10 times {
        router.tell(Ping, testProbe.ref)
        testProbe.expectMsg(Pong)
      }

      val routerMetrics = RouterMembers.instruments(
        TagSet.from(Map("path" -> "RouterMetricsSpec/user/picking-the-right-dispatcher-in-pool-router"))
      )

      routerMetrics
        .map(_._1.get(plain("dispatcher"))) should contain only ("custom-dispatcher")
    }

    "clean the pending messages metric when a routee dies in pool routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      val router = createTestPoolRouter("cleanup-pending-messages-in-pool-router", true)
      def pendingMessagesDistribution = RouterPendingMessages
        .withTags(routerTags("RouterMetricsSpec/user/cleanup-pending-messages-in-pool-router")).distribution()

      10 times { router.tell(RouterTrackTimings(sleep = Some(1 second)), timingsListener.ref) }
      1 times { router.tell(Die, timingsListener.ref) }
      500 times { router.tell(Discard, timingsListener.ref) }

      eventually {
        pendingMessagesDistribution.max should be >= (500L)
      }

      10 times { timingsListener.expectMsgType[RouterTrackedTimings] }

      eventually {
        pendingMessagesDistribution.max should be(0L)
      }
    }

    "clean the pending messages metric when a routee dies in balancing pool routers" in new RouterMetricsFixtures {
      val timingsListener = TestProbe()
      val router = createTestBalancingPoolRouter("cleanup-pending-messages-in-balancing-pool-router", true)
      def pendingMessagesDistribution = RouterPendingMessages
        .withTags(routerTags("RouterMetricsSpec/user/cleanup-pending-messages-in-balancing-pool-router")
          .withTags(
            TagSet.from(Map("dispatcher" -> "BalancingPool-/cleanup-pending-messages-in-balancing-pool-router"))
          )).distribution()

      10 times { router.tell(RouterTrackTimings(sleep = Some(1 second)), timingsListener.ref) }
      1 times { router.tell(Die, timingsListener.ref) }
      500 times { router.tell(Discard, timingsListener.ref) }

      eventually {
        pendingMessagesDistribution.max should be >= (100L)
      }

      10 times { timingsListener.expectMsgType[RouterTrackedTimings] }

      eventually {
        pendingMessagesDistribution.max should be(0L)
      }
    }

    "clean up the associated recorder when the pool router is stopped" in new RouterMetricsFixtures {
      val trackedRouter = createTestPoolRouter("stop-in-pool-router")
      RouterProcessingTime.tagValues("path") should contain("RouterMetricsSpec/user/stop-in-pool-router")

      // Killing the router should remove it's RouterMetrics and registering again below should create a new one.
      val deathWatcher = TestProbe()
      deathWatcher.watch(trackedRouter)
      trackedRouter ! PoisonPill
      deathWatcher.expectTerminated(trackedRouter)

      eventually {
        RouterProcessingTime.tagValues("path") shouldNot contain("RouterMetricsSpec/user/stop-in-pool-router")
      }
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = 5 seconds, interval = 5 milliseconds)

  override protected def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }

  def routerTags(path: String): TagSet = {
    val routerClass = if (path.contains("balancing")) "org.apache.pekko.routing.BalancingPool"
    else "org.apache.pekko.routing.RoundRobinPool"

    TagSet.from(
      Map(
        "path" -> path,
        "system" -> "RouterMetricsSpec",
        "dispatcher" -> "pekko.actor.default-dispatcher",
        "routeeClass" -> "kamon.instrumentation.pekko.RouterMetricsTestActor",
        "routerClass" -> routerClass
      )
    )
  }

  trait RouterMetricsFixtures {
    def createTestGroupRouter(routerName: String, resetState: Boolean = false): ActorRef = {
      val routees = Vector.fill(5) {
        system.actorOf(Props[RouterMetricsTestActor])
      }

      val group = system.actorOf(RoundRobinGroup(routees.map(_.path.toStringWithoutAddress)).props(), routerName)
      val initialiseListener = TestProbe()

      // Ensure that the router has been created before returning.
      group.tell(Ping, initialiseListener.ref)
      initialiseListener.expectMsg(Pong)

      // Cleanup all the metric recording instruments:
      if (resetState) {
        RouterRoutingTime.withTags(routerTags(s"RouterMetricsSpec/user/$routerName")).distribution(resetState = true)
        RouterTimeInMailbox.withTags(routerTags(s"RouterMetricsSpec/user/$routerName")).distribution(resetState = true)
        RouterProcessingTime.withTags(routerTags(s"RouterMetricsSpec/user/$routerName")).distribution(resetState = true)
        RouterErrors.withTags(routerTags(s"RouterMetricsSpec/user/$routerName")).value(resetState = true)
      }

      group
    }

    def createTestPoolRouter(routerName: String, resetState: Boolean = false): ActorRef = {
      val router = system.actorOf(RoundRobinPool(5).props(Props[RouterMetricsTestActor]), routerName)
      val initialiseListener = TestProbe()

      // Ensure that the router has been created before returning.
      router.tell(Ping, initialiseListener.ref)
      initialiseListener.expectMsg(Pong)

      // Cleanup all the metric recording instruments:
      if (resetState) {
        RouterRoutingTime.withTags(routerTags(s"RouterMetricsSpec/user/$routerName")).distribution(resetState = true)
        RouterTimeInMailbox.withTags(routerTags(s"RouterMetricsSpec/user/$routerName")).distribution(resetState = true)
        RouterProcessingTime.withTags(routerTags(s"RouterMetricsSpec/user/$routerName")).distribution(resetState = true)
        RouterErrors.withTags(routerTags(s"RouterMetricsSpec/user/$routerName")).value(resetState = true)
        RouterPendingMessages.withTags(routerTags(s"RouterMetricsSpec/user/$routerName")).distribution(resetState =
          true
        )
        RouterMembers.withTags(routerTags(s"RouterMetricsSpec/user/$routerName")).distribution(resetState = true)
      }

      router
    }

    def createTestBalancingPoolRouter(routerName: String, resetState: Boolean = false): ActorRef = {
      val router = system.actorOf(BalancingPool(5).props(Props[RouterMetricsTestActor]), routerName)
      val initialiseListener = TestProbe()

      // Ensure that the router has been created before returning.
      router.tell(Ping, initialiseListener.ref)
      initialiseListener.expectMsg(Pong)

      // Cleanup all the metric recording instruments:
      if (resetState) {
        val tags = routerTags(s"RouterMetricsSpec/user/$routerName")
          .withTag("dispatcher", s"BalancingPool-/$routerName")

        RouterRoutingTime.withTags(tags).distribution(resetState = true)
        RouterTimeInMailbox.withTags(tags).distribution(resetState = true)
        RouterProcessingTime.withTags(tags).distribution(resetState = true)
        RouterPendingMessages.withTags(tags).distribution(resetState = true)
        RouterMembers.withTags(tags).distribution(resetState = true)
        RouterErrors.withTags(tags).value(resetState = true)
      }

      router
    }
  }
}
