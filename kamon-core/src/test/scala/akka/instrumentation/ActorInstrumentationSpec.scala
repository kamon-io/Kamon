package akka.instrumentation

import org.scalatest.{WordSpecLike, Matchers}
import akka.actor.{ActorRef, Actor, Props, ActorSystem}

import akka.testkit.{ImplicitSender, TestKit}
import kamon.{TraceContext, Tracer}
import akka.pattern.{pipe, ask}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.routing.RoundRobinRouter


class ActorInstrumentationSpec extends TestKit(ActorSystem("ActorInstrumentationSpec")) with WordSpecLike with Matchers with ImplicitSender {
  implicit val executionContext = system.dispatcher

  "an instrumented actor ref" when {
    "used inside the context of a transaction" should {
      "propagate the trace context using bang" in new TraceContextEchoFixture {
        echo ! "test"

        expectMsg(Some(testTraceContext))
      }

      "propagate the trace context using tell" in new TraceContextEchoFixture {
        echo.tell("test", testActor)

        expectMsg(Some(testTraceContext))
      }

      "propagate the trace context using ask" in new TraceContextEchoFixture {
        implicit val timeout = Timeout(1 seconds)
        (echo ? "test") pipeTo(testActor)

        expectMsg(Some(testTraceContext))
      }

      "propagate the trace context to actors behind a rounter" in new RoutedTraceContextEchoFixture {
        val contexts: Seq[Option[TraceContext]] = for(_ <- 1 to 10) yield Some(tellWithNewContext(echo, "test"))

        expectMsgAllOf(contexts: _*)
      }
    }
  }

  trait TraceContextEchoFixture {
    val testTraceContext = Tracer.newTraceContext()
    val echo = system.actorOf(Props[TraceContextEcho])

    Tracer.set(testTraceContext)
  }

  trait RoutedTraceContextEchoFixture extends TraceContextEchoFixture {
    override val echo = system.actorOf(Props[TraceContextEcho].withRouter(RoundRobinRouter(nrOfInstances = 10)))

    def tellWithNewContext(target: ActorRef, message: Any): TraceContext = {
      val context = Tracer.newTraceContext()
      Tracer.set(context)

      target ! message
      context
    }
  }

}

class TraceContextEcho extends Actor {
  def receive = {
    case msg: String ⇒ sender ! Tracer.context()
  }
}


