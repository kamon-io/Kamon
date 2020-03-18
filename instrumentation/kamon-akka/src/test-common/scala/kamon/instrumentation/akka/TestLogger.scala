package kamon.instrumentation.akka

import akka.actor.Actor
import akka.event.Logging.LogEvent
import akka.event.slf4j.Slf4jLogger

class TestLogger extends Slf4jLogger {
  override def receive: PartialFunction[Any, Unit] = {
    val filteredReceive: Actor.Receive = {
      case event: LogEvent if(shouldDropEvent(event)) =>
    }

    filteredReceive.orElse(super.receive)
  }

  private def shouldDropEvent(event: LogEvent): Boolean =
    Option(event.message)
      .map(_.toString)
      .map(message => {
        message.contains("Division") ||
        message.contains("ask pattern")
      }).getOrElse(false)
}
