package kamon.trace.instrumentation

import akka.testkit.TestKit
import org.scalatest.{Inspectors, Matchers, WordSpecLike}
import akka.actor.{Props, ActorLogging, Actor, ActorSystem}
import akka.event.Logging.{LogEvent}
import kamon.trace.{ContextAware, TraceContext, Trace}

class ActorLoggingSpec extends TestKit(ActorSystem("actor-logging-spec")) with WordSpecLike with Matchers with Inspectors {

  "the ActorLogging instrumentation" should {
    "attach the TraceContext (if available) to log events" in {
      val testTraceContext = Some(TraceContext(Actor.noSender, 1))
      val loggerActor = system.actorOf(Props[LoggerActor])
      system.eventStream.subscribe(testActor, classOf[LogEvent])

      Trace.withContext(testTraceContext) {
        loggerActor ! "info"
      }

      fishForMessage() {
        case event: LogEvent if event.message.toString contains "TraceContext =>" =>
          val ctxInEvent = event.asInstanceOf[ContextAware].traceContext
          ctxInEvent === testTraceContext

        case event: LogEvent => false
      }
    }
  }
}

class LoggerActor extends Actor with ActorLogging {
  def receive = {
    case "info" => log.info("TraceContext => {}", Trace.context())
  }
}
