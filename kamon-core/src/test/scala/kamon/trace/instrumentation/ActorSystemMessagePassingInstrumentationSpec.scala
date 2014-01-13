package kamon.trace.instrumentation

import akka.testkit.{ ImplicitSender, TestKit }
import akka.actor._
import org.scalatest.WordSpecLike
import kamon.trace.Trace
import scala.util.control.NonFatal
import akka.actor.SupervisorStrategy.{ Escalate, Stop, Restart, Resume }
import scala.concurrent.duration._

class ActorSystemMessagePassingInstrumentationSpec extends TestKit(ActorSystem("actor-message-passing-tracing-spec")) with WordSpecLike with ImplicitSender {
  implicit val executionContext = system.dispatcher

  "the system message passing instrumentation" should {
    "keep the TraceContext while processing the Create message in top level actors" in new TraceContextFixture {
      Trace.withContext(testTraceContext) {
        system.actorOf(Props(new Actor {

          testActor ! Trace.context()

          def receive: Actor.Receive = { case any ⇒ }
        }))
      }

      expectMsg(testTraceContext)
    }

    "keep the TraceContext while processing the Create message in non top level actors" in new TraceContextFixture {
      Trace.withContext(testTraceContext) {
        system.actorOf(Props(new Actor {
          def receive: Actor.Receive = {
            case any ⇒
              context.actorOf(Props(new Actor {

                testActor ! Trace.context()

                def receive: Actor.Receive = { case any ⇒ }
              }))
          }
        })) ! "any"
      }

      expectMsg(testTraceContext)
    }

    "keep the TraceContext in the supervision cycle" when {
      "the actor is resumed" in new TraceContextFixture {
        val supervisor = supervisorWithDirective(Resume)

        Trace.withContext(testTraceContext) {
          supervisor ! "fail"
        }

        expectMsg(testTraceContext) // From the parent executing the supervision strategy

        // Ensure we didn't tie the actor with the context
        supervisor ! "context"
        expectMsg(None)
      }

      "the actor is restarted" in new TraceContextFixture {
        val supervisor = supervisorWithDirective(Restart, sendPreRestart = true, sendPostRestart = true)

        Trace.withContext(testTraceContext) {
          supervisor ! "fail"
        }

        expectMsg(testTraceContext) // From the parent executing the supervision strategy
        expectMsg(testTraceContext) // From the preRestart hook
        expectMsg(testTraceContext) // From the postRestart hook

        // Ensure we didn't tie the actor with the context
        supervisor ! "context"
        expectMsg(None)
      }

      "the actor is stopped" in new TraceContextFixture {
        val supervisor = supervisorWithDirective(Stop, sendPostStop = true)

        Trace.withContext(testTraceContext) {
          supervisor ! "fail"
        }

        expectMsg(testTraceContext) // From the parent executing the supervision strategy
        expectMsg(testTraceContext) // From the postStop hook
        expectNoMsg(1 second)
      }

      "the failure is escalated" in new TraceContextFixture {
        val supervisor = supervisorWithDirective(Escalate, sendPostStop = true)

        Trace.withContext(testTraceContext) {
          supervisor ! "fail"
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
        case NonFatal(throwable) ⇒ testActor ! Trace.context(); Stop
      }

      def receive = {
        case any ⇒ child forward any
      }
    }

    class Parent extends Actor {
      val child = context.actorOf(Props(new Child))

      override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
        case NonFatal(throwable) ⇒ testActor ! Trace.context(); directive
      }

      def receive: Actor.Receive = {
        case any ⇒ child forward any
      }

      override def postStop(): Unit = {
        if (sendPostStop) testActor ! Trace.context()
        super.postStop()
      }
    }

    class Child extends Actor {
      def receive = {
        case "fail"    ⇒ 1 / 0
        case "context" ⇒ sender ! Trace.context()
      }

      override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
        if (sendPreRestart) testActor ! Trace.context()
        super.preRestart(reason, message)
      }

      override def postRestart(reason: Throwable): Unit = {
        if (sendPostRestart) testActor ! Trace.context()
        super.postRestart(reason)
      }

      override def postStop(): Unit = {
        if (sendPostStop) testActor ! Trace.context()
        super.postStop()
      }

      override def preStart(): Unit = {
        if (sendPreStart) testActor ! Trace.context()
        super.preStart()
      }
    }

    system.actorOf(Props(new GrandParent))
  }
}
