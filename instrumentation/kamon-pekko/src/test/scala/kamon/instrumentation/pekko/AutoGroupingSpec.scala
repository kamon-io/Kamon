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
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, MetricInspection}
import org.scalactic.TimesOnInt.convertIntToRepeater
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class AutoGroupingSpec extends TestKit(ActorSystem("AutoGroupingSpec")) with AnyWordSpecLike with MetricInspection.Syntax
  with InstrumentInspection.Syntax with Matchers with InitAndStopKamonAfterAll with ImplicitSender with Eventually {

  import AutoGroupingSpec._

  val preExistingGroups = PekkoMetrics.GroupMembers.tagValues("group")

  "auto-grouping" should {
    "ignore actors that belong to defined groups or are being tracked" in {
      system.actorOf(reproducer(1, 0), "tracked-reproducer") ! "ping"
      system.actorOf(dummy(), "tracked-dummy")
      expectMsg("pong")

      withoutPreExisting(PekkoMetrics.GroupMembers.tagValues("group")) shouldBe empty
    }

    "ignore routers and routees" in {
      createTestRouter("tracked-router") ! "ping"
      createTestRouter("tracked-explicitly-excluded-router") ! "ping"
      expectMsg("pong")
      expectMsg("pong")

      withoutPreExisting(PekkoMetrics.GroupMembers.tagValues("group")) shouldBe empty
    }

    "automatically create groups for actors that are not being explicitly tracked" in {

      // This will create three levels of actors, all of type "Reproducer" and all should be auto-grouped on their
      // own level.
      system.actorOf(reproducer(3, 2))
      system.actorOf(dummy())

      eventually {
        withoutPreExisting(PekkoMetrics.GroupMembers.tagValues("group")) should contain allOf (
          "AutoGroupingSpec/user/Dummy",
          "AutoGroupingSpec/user/Reproducer",
          "AutoGroupingSpec/user/Reproducer/Reproducer",
          "AutoGroupingSpec/user/Reproducer/Reproducer/Reproducer"
        )
      }

      val topGroup = PekkoMetrics.forGroup("AutoGroupingSpec/user/Reproducer", system.name)
      val secondLevelGroup = PekkoMetrics.forGroup("AutoGroupingSpec/user/Reproducer/Reproducer", system.name)
      val thirdLevelGroup = PekkoMetrics.forGroup("AutoGroupingSpec/user/Reproducer/Reproducer/Reproducer", system.name)
      val dummyGroup = PekkoMetrics.forGroup("AutoGroupingSpec/user/Dummy", system.name)

      eventually {
        topGroup.members.distribution(resetState = false).max shouldBe 1
        secondLevelGroup.members.distribution(resetState = false).max shouldBe 2
        thirdLevelGroup.members.distribution(resetState = false).max shouldBe 8
        dummyGroup.members.distribution(resetState = false).max shouldBe 1
      }
    }
  }

  def withoutPreExisting(values: Seq[String]): Seq[String] =
    values.filter(v => preExistingGroups.indexOf(v) < 0)

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5 seconds, interval = 5 milliseconds)

  override protected def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }

  def createTestRouter(routerName: String): ActorRef = {
    val router = system.actorOf(RoundRobinPool(5).props(reproducer(1, 0)), routerName)
    val initialiseListener = TestProbe()

    // Ensure that the router has been created before returning.
    router.tell("ping", initialiseListener.ref)
    initialiseListener.expectMsg("pong")

    router
  }
}

object AutoGroupingSpec {

  class Reproducer(pendingDepth: Int, childCount: Int) extends Actor {

    override def receive: Receive = {
      case "ping" => sender() ! "pong"
      case other  => context.children.foreach(_.forward(other))
    }

    override def preStart(): Unit = {
      super.preStart()

      if(pendingDepth >= 0) {
        childCount.times {
          context.actorOf(reproducer(pendingDepth - 1, childCount * 2)) ! "ping"
        }
      }
    }
  }

  class Dummy extends Actor {
    override def receive: Receive = {
      case _ =>
    }
  }

  def reproducer(depth: Int, childCount: Int): Props =
    Props(new Reproducer(depth - 1, childCount))

  def dummy(): Props =
    Props[Dummy]
}

