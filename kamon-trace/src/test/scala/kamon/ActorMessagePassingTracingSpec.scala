package kamon

import org.scalatest.{WordSpecLike, Matchers}
import akka.actor.{ActorRef, Actor, Props, ActorSystem}

import akka.testkit.{ImplicitSender, TestKit}
import kamon.trace.Trace
import akka.pattern.{pipe, ask}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
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
          (ctxEchoActor ? "test") pipeTo(testActor)
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




