package kamon

import akka.testkit.TestKit
import akka.actor.{Props, Actor, ActorSystem}
import org.scalatest.{Matchers, WordSpecLike}
import akka.event.Logging.Warning
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import kamon.trace.{Trace, ContextAware}
import org.scalatest.OptionValues._


class AskPatternTracingSpec extends TestKit(ActorSystem("ask-pattern-tracing-spec")) with WordSpecLike with Matchers {

  "the AskPatternTracing" should {
    "log a warning with a stack trace and TraceContext taken from the moment the ask was triggered" in {
      implicit val ec = system.dispatcher
      implicit val timeout = Timeout(10 milliseconds)
      val noReply = system.actorOf(Props[NoReply])
      system.eventStream.subscribe(testActor, classOf[Warning])

      within(500 milliseconds) {
        val initialCtx = Trace.start("ask-test")
        noReply ? "hello"

        val warn = expectMsgPF() {
          case warn: Warning if warn.message.toString.contains("Timeout triggered for ask pattern") => warn
        }
        val capturedCtx = warn.asInstanceOf[ContextAware].traceContext

        capturedCtx should be('defined)
        capturedCtx.value should equal (initialCtx)
      }
    }
  }
}

class NoReply extends Actor {
  def receive = {
    case any =>
  }
}
