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

import akka.actor.{ Actor, ActorSystem, Props }
import akka.pattern.{ ask, pipe }
import akka.routing._
import akka.testkit.{ TestKitBase, ImplicitSender, TestKit }
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import kamon.trace.TraceRecorder
import org.scalatest.{ BeforeAndAfterAll, WordSpecLike }

import scala.concurrent.duration._

class ActorCellInstrumentationSpec extends TestKitBase with WordSpecLike with ImplicitSender with BeforeAndAfterAll {
  implicit lazy val system: ActorSystem = ActorSystem("actor-cell-instrumentation-spec")
  implicit val executionContext = system.dispatcher

  "the message passing instrumentation" should {
    "propagate the TraceContext using bang" in new EchoActorFixture {
      val testTraceContext = TraceRecorder.withNewTraceContext("bang-reply") {
        ctxEchoActor ! "test"
        TraceRecorder.currentContext
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext using tell" in new EchoActorFixture {
      val testTraceContext = TraceRecorder.withNewTraceContext("tell-reply") {
        ctxEchoActor.tell("test", testActor)
        TraceRecorder.currentContext
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext using ask" in new EchoActorFixture {
      implicit val timeout = Timeout(1 seconds)
      val testTraceContext = TraceRecorder.withNewTraceContext("ask-reply") {
        // The pipe pattern use Futures internally, so FutureTracing test should cover the underpinnings of it.
        (ctxEchoActor ? "test") pipeTo (testActor)
        TraceRecorder.currentContext
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext to actors behind a simple router" in new EchoSimpleRouterFixture {
      val testTraceContext = TraceRecorder.withNewTraceContext("router-reply") {
        router.route("test", testActor)
        TraceRecorder.currentContext
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext to actors behind a pool router" in new EchoPoolRouterFixture {
      val testTraceContext = TraceRecorder.withNewTraceContext("router-reply") {
        pool ! "test"
        TraceRecorder.currentContext
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext to actors behind a group router" in new EchoGroupRouterFixture {
      val testTraceContext = TraceRecorder.withNewTraceContext("router-reply") {
        group ! "test"
        TraceRecorder.currentContext
      }

      expectMsg(testTraceContext)
    }
  }

  override protected def afterAll(): Unit = shutdown()

  trait EchoActorFixture {
    val ctxEchoActor = system.actorOf(Props[TraceContextEcho])
  }

  trait EchoSimpleRouterFixture {
    val router = {
      val routees = Vector.fill(5) {
        val r = system.actorOf(Props[TraceContextEcho])
        ActorRefRoutee(r)
      }
      Router(RoundRobinRoutingLogic(), routees)
    }
  }

  trait EchoPoolRouterFixture {
    val pool = system.actorOf(RoundRobinPool(nrOfInstances = 5).props(Props[TraceContextEcho]), "pool-router")
  }

  trait EchoGroupRouterFixture {
    val routees = Vector.fill(5) {
      system.actorOf(Props[TraceContextEcho])
    }

    val group = system.actorOf(RoundRobinGroup(routees.map(_.path.toStringWithoutAddress)).props(), "group-router")
  }
}

class TraceContextEcho extends Actor {
  def receive = {
    case msg: String ⇒ sender ! TraceRecorder.currentContext
  }
}

