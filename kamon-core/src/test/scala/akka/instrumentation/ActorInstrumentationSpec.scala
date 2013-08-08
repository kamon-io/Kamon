package akka.instrumentation

import org.scalatest.{WordSpecLike, Matchers}
import akka.actor.{Actor, Props, ActorSystem}

import akka.testkit.{ImplicitSender, TestKit}
import kamon.{TraceContext, Kamon}


class ActorInstrumentationSpec extends TestKit(ActorSystem("ActorInstrumentationSpec")) with WordSpecLike with Matchers with ImplicitSender {

  "an instrumented actor ref" when {
    "used inside the context of a transaction" should {
      "propagate the trace context using bang" in new TraceContextEchoFixture {
        echo ! "test"

        expectMsg(Some(testTraceContext))
      }

      "propagate the trace context using tell" in {

      }

      "propagate the trace context using ask" in {

      }
    }
  }

  trait TraceContextEchoFixture {
    val testTraceContext = Kamon.newTraceContext()
    val echo = system.actorOf(Props[TraceContextEcho])

    Kamon.set(testTraceContext)
  }

}

class TraceContextEcho extends Actor {
  def receive = {
    case msg ⇒ sender ! Kamon.context()
  }
}


