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

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.{ask, pipe}
import akka.routing._
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import kamon.Kamon
import kamon.testkit.ContextTesting
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._

class ActorCellInstrumentationSpec extends TestKit(ActorSystem("ActorCellInstrumentationSpec")) with WordSpecLike
    with ContextTesting with BeforeAndAfterAll with ImplicitSender {
  implicit lazy val executionContext = system.dispatcher

  "the message passing instrumentation" should {
    "capture and propagate the current context when using bang" in new EchoActorFixture {
      Kamon.withContext(contextWithLocal("propagate-with-bang")) {
        baggageEchoActor ! "test"
      }

      expectMsg("propagate-with-bang")
    }

    "capture and propagate the current context for messages sent when the target actor might be a repointable ref" in {
      for (_ ← 1 to 10000) {
        val ta = system.actorOf(Props[PropagateBaggageEcho])
        Kamon.withContext(contextWithLocal("propagate-with-tell")) {
          ta.tell("test", testActor)
        }

        expectMsg("propagate-with-tell")
        system.stop(ta)
      }
    }

    "propagate the current context when using the ask pattern" in new EchoActorFixture {
      implicit val timeout = Timeout(1 seconds)
      Kamon.withContext(contextWithLocal("propagate-with-ask")) {
        // The pipe pattern use Futures internally, so FutureTracing test should cover the underpinnings of it.
        (baggageEchoActor ? "test") pipeTo (testActor)
      }

      expectMsg("propagate-with-ask")
    }


    "propagate the current context to actors behind a simple router" in new EchoSimpleRouterFixture {
      Kamon.withContext(contextWithLocal("propagate-with-router")) {
        router.route("test", testActor)
      }

      expectMsg("propagate-with-router")
    }

    "propagate the current context to actors behind a pool router" in new EchoPoolRouterFixture {
      Kamon.withContext(contextWithLocal("propagate-with-pool")) {
        pool ! "test"
      }

      expectMsg("propagate-with-pool")
    }

    "propagate the current context to actors behind a group router" in new EchoGroupRouterFixture {
      Kamon.withContext(contextWithLocal("propagate-with-group")) {
        group ! "test"
      }

      expectMsg("propagate-with-group")
    }
  }

  override protected def afterAll(): Unit = shutdown()

  trait EchoActorFixture {
    val baggageEchoActor = system.actorOf(Props[PropagateBaggageEcho])
  }

  trait EchoSimpleRouterFixture {
    val router = {
      val routees = Vector.fill(5) {
        val r = system.actorOf(Props[PropagateBaggageEcho])
        ActorRefRoutee(r)
      }
      Router(RoundRobinRoutingLogic(), routees)
    }
  }

  trait EchoPoolRouterFixture {
    val pool = system.actorOf(RoundRobinPool(nrOfInstances = 5).props(Props[PropagateBaggageEcho]), "pool-router")
  }

  trait EchoGroupRouterFixture {
    val routees = Vector.fill(5) {
      system.actorOf(Props[PropagateBaggageEcho])
    }

    val group = system.actorOf(RoundRobinGroup(routees.map(_.path.toStringWithoutAddress)).props(), "group-router")
  }
}

class PropagateBaggageEcho extends Actor with ContextTesting {
  def receive = {
    case _: String ⇒
      sender ! Kamon.currentContext().get(StringKey).getOrElse("MissingContext")
  }
}

