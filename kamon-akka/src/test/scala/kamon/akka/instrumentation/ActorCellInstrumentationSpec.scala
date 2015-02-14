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
import kamon.trace.TraceContext

import scala.concurrent.duration._

class ActorCellInstrumentationSpec extends BaseKamonSpec("actor-cell-instrumentation-spec") {
  implicit lazy val executionContext = system.dispatcher

  "the message passing instrumentation" should {
    "propagate the TraceContext using bang" in new EchoActorFixture {
      val testTraceContext = TraceContext.withContext(newContext("bang-reply")) {
        ctxEchoActor ! "test"
        TraceContext.currentContext
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext using tell" in new EchoActorFixture {
      val testTraceContext = TraceContext.withContext(newContext("tell-reply")) {
        ctxEchoActor.tell("test", testActor)
        TraceContext.currentContext
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext using ask" in new EchoActorFixture {
      implicit val timeout = Timeout(1 seconds)
      val testTraceContext = TraceContext.withContext(newContext("ask-reply")) {
        // The pipe pattern use Futures internally, so FutureTracing test should cover the underpinnings of it.
        (ctxEchoActor ? "test") pipeTo (testActor)
        TraceContext.currentContext
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext to actors behind a pool router" in new RoundRobinRouterFixture {
      val testTraceContext = TraceContext.withContext(newContext("router-reply")) {
        router ! "test"
        TraceContext.currentContext
      }

      expectMsg(testTraceContext)
    }

  }

  trait EchoActorFixture {
    val ctxEchoActor = system.actorOf(Props[TraceContextEcho])
  }

  trait RoundRobinRouterFixture {
    val router = system.actorOf(Props[TraceContextEcho].withRouter(
      RoundRobinRouter(nrOfInstances = 5)), "pool-router")
  }

}

class TraceContextEcho extends Actor {
  def receive = {
    case msg: String ⇒ sender ! TraceContext.currentContext
  }
}

