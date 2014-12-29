package kamon.instrumentation.akka

import akka.actor.SupervisorStrategy.{ Escalate, Restart, Resume, Stop }
import akka.actor._
import akka.testkit.{ TestKitBase, ImplicitSender }
import com.typesafe.config.ConfigFactory
import kamon.trace.{ EmptyTraceContext, TraceRecorder }
import org.scalatest.WordSpecLike

import scala.concurrent.duration._
import scala.util.control.NonFatal

class ActorSystemMessageInstrumentationSpec extends TestKitBase with WordSpecLike with ImplicitSender {
  implicit lazy val system: ActorSystem = ActorSystem("actor-system-message-instrumentation-spec", ConfigFactory.parseString(
    """
      |akka.loglevel = OFF
    """.stripMargin))

  implicit val executionContext = system.dispatcher

  "the system message passing instrumentation" should {
    "keep the TraceContext while processing the Create message in top level actors" in {
      val testTraceContext = TraceRecorder.withNewTraceContext("creating-top-level-actor") {
        system.actorOf(Props(new Actor {
          testActor ! TraceRecorder.currentContext
          def receive: Actor.Receive = { case any ⇒ }
        }))

        TraceRecorder.currentContext
      }

      expectMsg(testTraceContext)
    }

    "keep the TraceContext while processing the Create message in non top level actors" in {
      val testTraceContext = TraceRecorder.withNewTraceContext("creating-non-top-level-actor") {
        system.actorOf(Props(new Actor {
          def receive: Actor.Receive = {
            case any ⇒
              context.actorOf(Props(new Actor {
                testActor ! TraceRecorder.currentContext
                def receive: Actor.Receive = { case any ⇒ }
              }))
          }
        })) ! "any"

        TraceRecorder.currentContext
      }

      expectMsg(testTraceContext)
    }

    "keep the TraceContext in the supervision cycle" when {
      "the actor is resumed" in {
        val supervisor = supervisorWithDirective(Resume)

        val testTraceContext = TraceRecorder.withNewTraceContext("fail-and-resume") {
          supervisor ! "fail"
          TraceRecorder.currentContext
        }

        expectMsg(testTraceContext) // From the parent executing the supervision strategy

        // Ensure we didn't tie the actor with the context
        supervisor ! "context"
        expectMsg(EmptyTraceContext)
      }

      "the actor is restarted" in {
        val supervisor = supervisorWithDirective(Restart, sendPreRestart = true, sendPostRestart = true)

        val testTraceContext = TraceRecorder.withNewTraceContext("fail-and-restart") {
          supervisor ! "fail"
          TraceRecorder.currentContext
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

        val testTraceContext = TraceRecorder.withNewTraceContext("fail-and-stop") {
          supervisor ! "fail"
          TraceRecorder.currentContext
        }

        expectMsg(testTraceContext) // From the parent executing the supervision strategy
        expectMsg(testTraceContext) // From the postStop hook
        expectNoMsg(1 second)
      }

      "the failure is escalated" in {
        val supervisor = supervisorWithDirective(Escalate, sendPostStop = true)

        val testTraceContext = TraceRecorder.withNewTraceContext("fail-and-escalate") {
          supervisor ! "fail"
          TraceRecorder.currentContext
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
        case NonFatal(throwable) ⇒ testActor ! TraceRecorder.currentContext; Stop
      }

      def receive = {
        case any ⇒ child forward any
      }
    }

    class Parent extends Actor {
      val child = context.actorOf(Props(new Child))

      override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
        case NonFatal(throwable) ⇒ testActor ! TraceRecorder.currentContext; directive
      }

      def receive: Actor.Receive = {
        case any ⇒ child forward any
      }

      override def postStop(): Unit = {
        if (sendPostStop) testActor ! TraceRecorder.currentContext
        super.postStop()
      }
    }

    class Child extends Actor {
      def receive = {
        case "fail"    ⇒ throw new ArithmeticException("Division by zero.")
        case "context" ⇒ sender ! TraceRecorder.currentContext
      }

      override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
        if (sendPreRestart) testActor ! TraceRecorder.currentContext
        super.preRestart(reason, message)
      }

      override def postRestart(reason: Throwable): Unit = {
        if (sendPostRestart) testActor ! TraceRecorder.currentContext
        super.postRestart(reason)
      }

      override def postStop(): Unit = {
        if (sendPostStop) testActor ! TraceRecorder.currentContext
        super.postStop()
      }

      override def preStart(): Unit = {
        if (sendPreStart) testActor ! TraceRecorder.currentContext
        super.preStart()
      }
    }

    system.actorOf(Props(new GrandParent))
  }
}
