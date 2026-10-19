/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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
import org.apache.pekko.routing.RoundRobinPool
import org.apache.pekko.testkit.{ImplicitSender, TestKit, TestProbe}
import ActorMetricsTestActor.{Block, Die}
import kamon.instrumentation.pekko.PekkoMetrics._
import kamon.tag.TagSet
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, MetricInspection}
import kamon.util.Filter
import org.scalactic.TimesOnInt.convertIntToRepeater
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.util.Random

class ActorGroupMetricsSpec extends TestKit(ActorSystem("ActorGroupMetricsSpec")) with AnyWordSpecLike
    with MetricInspection.Syntax
    with InstrumentInspection.Syntax with Matchers with InitAndStopKamonAfterAll with ImplicitSender with Eventually {

  "the Kamon actor-group metrics" should {
    "increase the member count when an actor matching the pattern is created" in new ActorGroupMetricsFixtures {
      val trackedActor1 = watch(createTestActor("group-of-actors-1"))
      val trackedActor2 = watch(createTestActor("group-of-actors-2"))
      val trackedActor3 = watch(createTestActor("group-of-actors-3"))
      val nonTrackedActor = createTestActor("someone-else")

      eventually(timeout(5 seconds)) {
        GroupMembers.withTags(groupTags("group-of-actors")).distribution().max shouldBe (3)
      }

      system.stop(trackedActor1)
      expectTerminated(trackedActor1)
      system.stop(trackedActor2)
      expectTerminated(trackedActor2)
      system.stop(trackedActor3)
      expectTerminated(trackedActor3)

      eventually(timeout(5 seconds)) {
        GroupMembers.withTags(groupTags("group-of-actors")).distribution().max shouldBe (0)
      }
    }

    "increase the member count when a routee matching the pattern is created" in new ActorGroupMetricsFixtures {
      val trackedRouter = createTestPoolRouter("group-of-routees")
      val nonTrackedRouter = createTestPoolRouter("non-tracked-group-of-routees")

      eventually(timeout(5 seconds)) {
        val valueNow = GroupMembers.withTags(groupTags("group-of-routees")).distribution().max
        valueNow shouldBe (5)
      }

      val trackedRouter2 = createTestPoolRouter("group-of-routees-2")
      val trackedRouter3 = createTestPoolRouter("group-of-routees-3")

      eventually(GroupMembers.withTags(groupTags("group-of-routees")).distribution().max shouldBe (15))

      system.stop(trackedRouter)
      system.stop(trackedRouter2)
      system.stop(trackedRouter3)

      eventually(GroupMembers.withTags(groupTags("group-of-routees")).distribution(resetState = true).max shouldBe (0))
    }

    "allow defining groups by configuration" in {
      PekkoInstrumentation.matchingActorGroups("system/user/group-provided-by-code-actor") shouldBe empty
      PekkoInstrumentation.defineActorGroup(
        "group-by-code",
        Filter.fromGlob("*/user/group-provided-by-code-actor")
      ) shouldBe true
      PekkoInstrumentation.matchingActorGroups(
        "system/user/group-provided-by-code-actor"
      ) should contain only ("group-by-code")
      PekkoInstrumentation.removeActorGroup("group-by-code")
      PekkoInstrumentation.matchingActorGroups("system/user/group-provided-by-code-actor") shouldBe empty
    }

    "cleanup pending-messages metric on member shutdown" in new ActorGroupMetricsFixtures {
      val actors = (1 to 10).map(id => watch(createTestActor(s"group-of-actors-for-cleaning-$id")))

      eventually {
        val memberCountDistribution = GroupMembers.withTags(groupTags("group-of-actors-for-cleaning")).distribution()
        memberCountDistribution.min shouldBe (10)
        memberCountDistribution.max shouldBe (10)
      }

      val hangQueue = Block(1 milliseconds)
      Random.shuffle(actors).foreach { groupMember =>
        1000 times {
          groupMember ! hangQueue
        }
      }

      actors.foreach(system.stop)

      eventually {
        val pendingMessagesDistribution =
          GroupPendingMessages.withTags(groupTags("group-of-actors-for-cleaning")).distribution()
        pendingMessagesDistribution.count should be > 0L
        pendingMessagesDistribution.max shouldBe 0L
      }
    }

    "cleanup pending-messages metric on member shutdown there are messages still being sent to the members" in new ActorGroupMetricsFixtures {
      val parent = watch(system.actorOf(Props[SecondLevelGrouping], "second-level-group"))

      eventually {
        val memberCountDistribution = GroupMembers.withTags(groupTags("second-level-group")).distribution()
        memberCountDistribution.min shouldBe (10)
        memberCountDistribution.max shouldBe (10)
      }

      1000 times {
        parent ! Die
      }

      eventually(timeout(5 seconds)) {
        val pendingMessagesDistribution = GroupPendingMessages.withTags(groupTags("second-level-group")).distribution()

        pendingMessagesDistribution.count should be > 0L
        // We leave some room here because there is a small period of time in which the instrumentation might increment
        // the mailbox-size range sampler if messages are being sent while shutting down the actor.
        //
        // TODO: Find a way to only increment the mailbox size when the messages are actually going to a mailbox.
        pendingMessagesDistribution.max should be(0L +- 5L)
      }
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = 5 seconds, interval = 5 milliseconds)

  override protected def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }

  def groupTags(group: String): TagSet =
    TagSet.from(
      Map(
        "system" -> "ActorGroupMetricsSpec",
        "group" -> group
      )
    )

  trait ActorGroupMetricsFixtures {
    def createTestActor(name: String): ActorRef = {
      val actor = system.actorOf(Props[ActorMetricsTestActor], name)
      val initialiseListener = TestProbe()

      // Ensure that the actor has been created before returning.
      actor.tell(ActorMetricsTestActor.Ping, initialiseListener.ref)
      initialiseListener.expectMsg(ActorMetricsTestActor.Pong)

      actor
    }

    def createTestPoolRouter(routerName: String): ActorRef = {
      val router = system.actorOf(RoundRobinPool(5).props(Props[RouterMetricsTestActor]), routerName)
      val initialiseListener = TestProbe()

      // Ensure that the router has been created before returning.
      router.tell(RouterMetricsTestActor.Ping, initialiseListener.ref)
      initialiseListener.expectMsg(RouterMetricsTestActor.Pong)

      router
    }
  }
}

class SecondLevelGrouping extends Actor {
  (1 to 10).foreach(id => context.actorOf(Props[ActorMetricsTestActor], s"child-$id"))

  def receive: Actor.Receive = {
    case any => Random.shuffle(context.children).headOption.foreach(_.forward(any))
  }
}
