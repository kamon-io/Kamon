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

package kamon.instrumentation.akka

import akka.actor.SupervisorStrategy.{ Escalate, Restart, Resume, Stop }
import akka.actor._
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import kamon.testkit.BaseKamonSpec
import kamon.trace.{ Tracer, EmptyTraceContext }
import org.scalatest.WordSpecLike

import scala.concurrent.duration._
import scala.util.control.NonFatal

class ActorSystemMessageInstrumentationSpec extends BaseKamonSpec("actor-system-message-instrumentation-spec") with WordSpecLike with ImplicitSender {
  implicit lazy val executionContext = system.dispatcher

  "the system message passing instrumentation" should {
    "keep the TraceContext while processing the Create message in top level actors" in {
      val testTraceContext = Tracer.withContext(newContext("creating-top-level-actor")) {
        system.actorOf(Props(new Actor {
          testActor ! Tracer.currentContext
          def receive: Actor.Receive = { case any ⇒ }
        }))

        Tracer.currentContext
      }

      expectMsg(testTraceContext)
    }

    "keep the TraceContext while processing the Create message in non top level actors" in {
      val testTraceContext = Tracer.withContext(newContext("creating-non-top-level-actor")) {
        system.actorOf(Props(new Actor {
          def receive: Actor.Receive = {
            case any ⇒
              context.actorOf(Props(new Actor {
                testActor ! Tracer.currentContext
                def receive: Actor.Receive = { case any ⇒ }
              }))
          }
        })) ! "any"

        Tracer.currentContext
      }

      expectMsg(testTraceContext)
    }

    "keep the TraceContext in the supervision cycle" when {
      "the actor is resumed" in {
        val supervisor = supervisorWithDirective(Resume)

        val testTraceContext = Tracer.withContext(newContext("fail-and-resume")) {
          supervisor ! "fail"
          Tracer.currentContext
        }

        expectMsg(testTraceContext) // From the parent executing the supervision strategy

        // Ensure we didn't tie the actor with the context
        supervisor ! "context"
        expectMsg(EmptyTraceContext)
      }

      "the actor is restarted" in {
        val supervisor = supervisorWithDirective(Restart, sendPreRestart = true, sendPostRestart = true)

        val testTraceContext = Tracer.withContext(newContext("fail-and-restart")) {
          supervisor ! "fail"
          Tracer.currentContext
        }

        expectMsg(testTraceContext) // From the parent executing the supervision strategy
        expectMsg(testTraceContext) // From the preRestart hook
        expectMsg(testTraceContext) // From the postRestart hook

        // Ensure we didn't tie the actor with the context
        supervisor ! "context"
        expectMsg(EmptyTraceContext)
      }

      "the actor is stopped" in {
        val supervisor = supervisorWithDirective(Stop, sendPostStop = true)

        val testTraceContext = Tracer.withContext(newContext("fail-and-stop")) {
          supervisor ! "fail"
          Tracer.currentContext
        }

        expectMsg(testTraceContext) // From the parent executing the supervision strategy
        expectMsg(testTraceContext) // From the postStop hook
        expectNoMsg(1 second)
      }

      "the failure is escalated" in {
        val supervisor = supervisorWithDirective(Escalate, sendPostStop = true)

        val testTraceContext = Tracer.withContext(newContext("fail-and-escalate")) {
          supervisor ! "fail"
          Tracer.currentContext
        }

        expectMsg(testTraceContext) // From the parent executing the supervision strategy
        expectMsg(testTraceContext) // From the grandparent executing the supervision strategy
        expectMsg(testTraceContext) // From the postStop hook in the child
        expectMsg(testTraceContext) // From the postStop hook in the parent
        expectNoMsg(1 second)
      }
    }
  }

  def supervisorWithDirective(directive: SupervisorStrategy.Directive, sendPreRestart: Boolean = false, sendPostRestart: Boolean = false,
    sendPostStop: Boolean = false, sendPreStart: Boolean = false): ActorRef = {
    class GrandParent extends Actor {
      val child = context.actorOf(Props(new Parent))

      override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
        case NonFatal(throwable) ⇒ testActor ! Tracer.currentContext; Stop
      }

      def receive = {
        case any ⇒ child forward any
      }
    }

    class Parent extends Actor {
      val child = context.actorOf(Props(new Child))

      override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
        case NonFatal(throwable) ⇒ testActor ! Tracer.currentContext; directive
      }

      def receive: Actor.Receive = {
        case any ⇒ child forward any
      }

      override def postStop(): Unit = {
        if (sendPostStop) testActor ! Tracer.currentContext
        super.postStop()
      }
    }

    class Child extends Actor {
      def receive = {
        case "fail"    ⇒ throw new ArithmeticException("Division by zero.")
        case "context" ⇒ sender ! Tracer.currentContext
      }

      override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
        if (sendPreRestart) testActor ! Tracer.currentContext
        super.preRestart(reason, message)
      }

      override def postRestart(reason: Throwable): Unit = {
        if (sendPostRestart) testActor ! Tracer.currentContext
        super.postRestart(reason)
      }

      override def postStop(): Unit = {
        if (sendPostStop) testActor ! Tracer.currentContext
        super.postStop()
      }

      override def preStart(): Unit = {
        if (sendPreStart) testActor ! Tracer.currentContext
        super.preStart()
      }
    }

    system.actorOf(Props(new GrandParent))
  }
}
