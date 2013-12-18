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
package kamon.trace.instrumentation

import org.scalatest.{ WordSpecLike, Matchers }
import akka.actor.{ ActorRef, Actor, Props, ActorSystem }

import akka.testkit.{ ImplicitSender, TestKit }
import kamon.trace.Trace
import akka.pattern.{ pipe, ask }
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import akka.routing.RoundRobinRouter
import kamon.trace.TraceContext

class ActorMessagePassingTracingSpec extends TestKit(ActorSystem("actor-message-passing-tracing-spec")) with WordSpecLike with ImplicitSender {
  implicit val executionContext = system.dispatcher

  "the message passing instrumentation" should {
    "propagate the TraceContext using bang" in new TraceContextEchoFixture {
      Trace.withContext(testTraceContext) {
        ctxEchoActor ! "test"
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext using tell" in new TraceContextEchoFixture {
      Trace.withContext(testTraceContext) {
        ctxEchoActor.tell("test", testActor)
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext using ask" in new TraceContextEchoFixture {
      implicit val timeout = Timeout(1 seconds)
      Trace.withContext(testTraceContext) {
        // The pipe pattern use Futures internally, so FutureTracing test should cover the underpinnings of it.
        (ctxEchoActor ? "test") pipeTo (testActor)
      }

      expectMsg(testTraceContext)
    }

    "propagate the TraceContext to actors behind a router" in new RoutedTraceContextEchoFixture {
      Trace.withContext(testTraceContext) {
        ctxEchoActor ! "test"
      }

      expectMsg(testTraceContext)
    }
  }

  trait TraceContextEchoFixture {
    val testTraceContext = Some(Trace.newTraceContext(""))
    val ctxEchoActor = system.actorOf(Props[TraceContextEcho])
  }

  trait RoutedTraceContextEchoFixture extends TraceContextEchoFixture {
    override val ctxEchoActor = system.actorOf(Props[TraceContextEcho].withRouter(RoundRobinRouter(nrOfInstances = 1)))
  }
}

class TraceContextEcho extends Actor {
  def receive = {
    case msg: String ⇒ sender ! Trace.context()
  }
}

