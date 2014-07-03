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
import akka.routing.RoundRobinPool
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout
import kamon.trace.TraceRecorder
import org.scalatest.WordSpecLike

import scala.concurrent.duration._

class ActorCellInstrumentationSpec extends TestKit(ActorSystem("actor-cell-instrumentation-spec")) with WordSpecLike
    with ImplicitSender {

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

    "propagate the TraceContext to actors behind a router" in new RoutedEchoActorFixture {
      val testTraceContext = TraceRecorder.withNewTraceContext("router-reply") {
        ctxEchoActor ! "test"
        TraceRecorder.currentContext
      }

      expectMsg(testTraceContext)
    }
  }

  trait EchoActorFixture {
    val ctxEchoActor = system.actorOf(Props[TraceContextEcho])
  }

  trait RoutedEchoActorFixture extends EchoActorFixture {
    override val ctxEchoActor = system.actorOf(Props[TraceContextEcho].withRouter(RoundRobinPool(nrOfInstances = 1)))
  }
}

class TraceContextEcho extends Actor {
  def receive = {
    case msg: String ⇒ sender ! TraceRecorder.currentContext
  }
}

