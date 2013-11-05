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

      "propagate the trace context to actors behind a router" in new RoutedTraceContextEchoFixture {
        val contexts: Seq[Option[TraceContext]] = for(_ <- 1 to 10) yield Some(tellWithNewContext(echo, "test"))

        expectMsgAllOf(contexts: _*)
      }

      /*"propagate with many asks" in {
        val echo = system.actorOf(Props[TraceContextEcho])
        val iterations = 50000
        implicit val timeout = Timeout(10 seconds)

        val futures = for(_ <- 1 to iterations) yield {
          Tracer.start
          val result = (echo ? "test")
          Tracer.clear

          result
        }

        val allResults = Await.result(Future.sequence(futures), 10 seconds)
        assert(iterations == allResults.collect {
          case Some(_) => 1
        }.sum)
      }*/
    }
  }

  trait TraceContextEchoFixture {
    val testTraceContext = Trace.newTraceContext()
    val echo = system.actorOf(Props[TraceContextEcho])

    Trace.set(testTraceContext)
  }

  trait RoutedTraceContextEchoFixture extends TraceContextEchoFixture {
    override val echo = system.actorOf(Props[TraceContextEcho].withRouter(RoundRobinRouter(nrOfInstances = 10)))

    def tellWithNewContext(target: ActorRef, message: Any): TraceContext = {
      val context = Trace.newTraceContext()
      Trace.set(context)

      target ! message
      context
    }
  }

}

class TraceContextEcho extends Actor {
  def receive = {
    case msg: String ⇒ sender ! Trace.context()
  }
}


