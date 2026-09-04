/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.instrumentation.akka

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.{ask, pipe}
import akka.routing._
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import kamon.Kamon
import kamon.tag.Lookups._
import kamon.testkit.{InitAndStopKamonAfterAll, MetricInspection}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


class ActorCellInstrumentationSpec extends TestKit(ActorSystem("ActorCellInstrumentationSpec")) with AnyWordSpecLike
    with BeforeAndAfterAll with ImplicitSender with Eventually with MetricInspection.Syntax with Matchers with InitAndStopKamonAfterAll {
  implicit lazy val executionContext: ExecutionContext = system.dispatcher
  import ContextTesting._

  "the message passing instrumentation" should {
    "capture and propagate the current context when using bang" in new EchoActorFixture {
      Kamon.runWithContext(testContext("propagate-with-bang")) {
        contextEchoActor ! "test"
      }

      expectMsg("propagate-with-bang")
    }

    "capture and propagate the current context for messages sent when the target actor might be a repointable ref" in {
      for (_ <- 1 to 100) {
        val ta = system.actorOf(Props[ContextStringEcho])
        Kamon.runWithContext(testContext("propagate-with-tell")) {
          ta.tell("test", testActor)
        }

        expectMsg("propagate-with-tell")
        system.stop(ta)
      }
    }

    "propagate the current context when using the ask pattern" in new EchoActorFixture {
      implicit val timeout: Timeout = Timeout(1 seconds)
      Kamon.runWithContext(testContext("propagate-with-ask")) {
        // The pipe pattern use Futures internally, so FutureTracing test should cover the underpinnings of it.
        (contextEchoActor ? "test") pipeTo (testActor)
      }

      expectMsg("propagate-with-ask")
    }


    "propagate the current context to actors behind a simple router" in new EchoSimpleRouterFixture {
      Kamon.runWithContext(testContext("propagate-with-router")) {
        router.route("test", testActor)
      }

      expectMsg("propagate-with-router")
    }

    "propagate the current context to actors behind a pool router" in new EchoPoolRouterFixture {
      Kamon.runWithContext(testContext("propagate-with-pool")) {
        pool ! "test"
      }

      expectMsg("propagate-with-pool")
    }

    "propagate the current context to actors behind a group router" in new EchoGroupRouterFixture {
      Kamon.runWithContext(testContext("propagate-with-group")) {
        group ! "test"
      }

      expectMsg("propagate-with-group")
    }

    "cleanup the metric recorders when a RepointableActorRef is killed early" in {
      def actorPathTag(ref: ActorRef): String = system.name + "/" + ref.path.elements.mkString("/")
      val trackedActors = new ListBuffer[String]

      for(j <- 1 to 10) {
        for (i <- 1 to 1000) {
          val a = system.actorOf(Props[ContextStringEcho], s"repointable-$j-$i")
          a ! PoisonPill
          trackedActors.append(actorPathTag(a))
        }

        eventually(timeout(1 second)) {
          val trackedActors = AkkaMetrics.ActorProcessingTime.tagValues("path")
          for(p <- trackedActors) {
            trackedActors.find(_ == p) shouldBe empty
          }
        }

        trackedActors.clear()
      }
    }
  }

  override protected def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }

  trait EchoActorFixture {
    val contextEchoActor: ActorRef = system.actorOf(Props[ContextStringEcho])
  }

  trait EchoSimpleRouterFixture {
    val router: Router = {
      val routees = Vector.fill(5) {
        val r = system.actorOf(Props[ContextStringEcho])
        ActorRefRoutee(r)
      }
      Router(RoundRobinRoutingLogic(), routees)
    }
  }

  trait EchoPoolRouterFixture {
    val pool: ActorRef = system.actorOf(RoundRobinPool(nrOfInstances = 5).props(Props[ContextStringEcho]), "pool-router")
  }

  trait EchoGroupRouterFixture {
    val routees: Vector[ActorRef] = Vector.fill(5) {
      system.actorOf(Props[ContextStringEcho])
    }

    val group: ActorRef = system.actorOf(RoundRobinGroup(routees.map(_.path.toStringWithoutAddress)).props(), "group-router")
  }
}

class ContextStringEcho extends Actor {
  import ContextTesting._

  def receive: Receive = {
    case _: String =>
      sender ! Kamon.currentContext().getTag(plain(TestKey))
  }
}

