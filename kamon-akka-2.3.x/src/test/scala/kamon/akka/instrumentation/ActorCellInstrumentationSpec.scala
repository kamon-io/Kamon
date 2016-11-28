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

import akka.actor.{ Actor, Props }
import akka.pattern.{ ask, pipe }
import akka.routing._
import akka.util.Timeout
import kamon.testkit.BaseKamonSpec
import kamon.trace.Tracer

import scala.concurrent.duration._

class ActorCellInstrumentationSpec extends BaseKamonSpec("actor-cell-instrumentation-spec") {
  implicit lazy val executionContext = system.dispatcher

  "the message passing instrumentation" should {
    "propagate the TraceContext using bang" in new EchoActorFixture {
      val testTraceContext = Tracer.withContext(newContext("bang-reply")) {
        ctxEchoActor ! "test"
        Tracer.currentContext
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext using tell" in new EchoActorFixture {
      for (i ← 1 to 10000) {
        val ta = system.actorOf(Props[TraceContextEcho])
        val testTraceContext = Tracer.withContext(newContext("tell-reply", i.toString)) {
          ta.tell("test", testActor)
          Tracer.currentContext
        }

        expectMsg(testTraceContext)
        system.stop(ta)
      }
    }

    "propagate the TraceContext using ask" in new EchoActorFixture {
      implicit val timeout = Timeout(1 seconds)
      val testTraceContext = Tracer.withContext(newContext("ask-reply")) {
        // The pipe pattern use Futures internally, so FutureTracing test should cover the underpinnings of it.
        (ctxEchoActor ? "test") pipeTo (testActor)
        Tracer.currentContext
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext to actors behind a simple router" in new EchoSimpleRouterFixture {
      val testTraceContext = Tracer.withContext(newContext("router-reply")) {
        router.route("test", testActor)
        Tracer.currentContext
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext to actors behind a pool router" in new EchoPoolRouterFixture {
      val testTraceContext = Tracer.withContext(newContext("router-reply")) {
        pool ! "test"
        Tracer.currentContext
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext to actors behind a group router" in new EchoGroupRouterFixture {
      val testTraceContext = Tracer.withContext(newContext("router-reply")) {
        group ! "test"
        Tracer.currentContext
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
    case msg: String ⇒
      sender ! Tracer.currentContext
  }
}

