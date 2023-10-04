/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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


import kamon.Kamon
import kamon.instrumentation.pekko.ContextTesting._
import kamon.tag.Lookups._
import org.apache.pekko.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import org.apache.pekko.actor._
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContextExecutor
import scala.util.control.NonFatal

class SystemMessageInstrumentationSpec extends TestKit(ActorSystem("ActorSystemMessageInstrumentationSpec")) with AnyWordSpecLike with Matchers
  with BeforeAndAfterAll with ImplicitSender {
  implicit lazy val executionContext: ExecutionContextExecutor = system.dispatcher

  "the system message passing instrumentation" should {
    "capture and propagate the current context while processing the Create message in top level actors" in {
      Kamon.runWithContext(testContext("creating-top-level-actor")) {
        system.actorOf(Props(new Actor {
          testActor ! propagatedContextKey()
          def receive: Actor.Receive = { case _ => }
        }))
      }

      expectMsg("creating-top-level-actor")
    }

    "capture and propagate the current context when processing the Create message in non top level actors" in {
      Kamon.runWithContext(testContext("creating-non-top-level-actor")) {
        system.actorOf(Props(new Actor {
          def receive: Actor.Receive = {
            case _ =>
              context.actorOf(Props(new Actor {
                testActor ! propagatedContextKey()
                def receive: Actor.Receive = { case _ => }
              }))
          }
        })) ! "any"
      }

      expectMsg("creating-non-top-level-actor")
    }

    "keep the current context in the supervision cycle" when {
      "the actor is resumed" in {
        val supervisor = supervisorWithDirective(Resume)
        Kamon.runWithContext(testContext("fail-and-resume")) {
          supervisor ! "fail"
        }

        expectMsg("fail-and-resume") // From the parent executing the supervision strategy

        // Ensure we didn't tie the actor with the initially captured context
        supervisor ! "context"
        expectMsg("MissingContext")
      }

      "the actor is restarted" in {
        val supervisor = supervisorWithDirective(Restart, sendPreRestart = true, sendPostRestart = true)
        Kamon.runWithContext(testContext("fail-and-restart")) {
          supervisor ! "fail"
        }

        expectMsg("fail-and-restart") // From the parent executing the supervision strategy
        expectMsg("fail-and-restart") // From the preRestart hook
        expectMsg("fail-and-restart") // From the postRestart hook

        // Ensure we didn't tie the actor with the context
        supervisor ! "context"
        expectMsg("MissingContext")
      }

      "the actor is stopped" in {
        val supervisor = supervisorWithDirective(Stop, sendPostStop = true)
        Kamon.runWithContext(testContext("fail-and-stop")) {
          supervisor ! "fail"
        }

        expectMsg("fail-and-stop") // From the parent executing the supervision strategy
        expectMsg("fail-and-stop") // From the postStop hook
        //TODO: FIXME expectNoMessage(1 second)
      }

      "the failure is escalated" in {
        val supervisor = supervisorWithDirective(Escalate, sendPostStop = true)
        Kamon.runWithContext(testContext("fail-and-escalate")) {
          supervisor ! "fail"
        }

        expectMsg("fail-and-escalate") // From the parent executing the supervision strategy
        expectMsg("fail-and-escalate") // From the grandparent executing the supervision strategy
        expectMsg("fail-and-escalate") // From the postStop hook in the child
        expectMsg("fail-and-escalate") // From the postStop hook in the parent
        //TODO: FIXME expectNoMessage(1 second)
      }
    }
  }

  private def propagatedContextKey(): String =
    Kamon.currentContext().getTag(option(TestKey)).getOrElse("MissingContext")

  def supervisorWithDirective(directive: SupervisorStrategy.Directive, sendPreRestart: Boolean = false, sendPostRestart: Boolean = false,
    sendPostStop: Boolean = false, sendPreStart: Boolean = false): ActorRef = {

    class GrandParent extends Actor {
      val child: ActorRef = context.actorOf(Props(new Parent))

      override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
        case NonFatal(_) => testActor ! propagatedContextKey(); Stop
      }

      def receive: Receive = {
        case any => child forward any
      }
    }

    class Parent extends Actor {
      val child: ActorRef = context.actorOf(Props(new Child))

      override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
        case NonFatal(_) => testActor ! propagatedContextKey(); directive
      }

      def receive: Actor.Receive = {
        case any => child forward any
      }

      override def postStop(): Unit = {
        if (sendPostStop) testActor ! propagatedContextKey()
        super.postStop()
      }
    }

    class Child extends Actor {
      def receive: Receive = {
        case "fail"    => throw new ArithmeticException("Division by zero.")
        case "context" => sender ! propagatedContextKey()
      }

      override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
        if (sendPreRestart) testActor ! propagatedContextKey()
        super.preRestart(reason, message)
      }

      override def postRestart(reason: Throwable): Unit = {
        if (sendPostRestart) testActor ! propagatedContextKey()
        super.postRestart(reason)
      }

      override def postStop(): Unit = {
        if (sendPostStop) testActor ! propagatedContextKey()
        super.postStop()
      }

      override def preStart(): Unit = {
        if (sendPreStart) testActor ! propagatedContextKey()
        super.preStart()
      }
    }

    system.actorOf(Props(new GrandParent))
  }
}

