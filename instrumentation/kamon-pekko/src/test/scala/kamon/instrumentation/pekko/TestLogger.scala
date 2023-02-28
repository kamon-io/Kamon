package kamon.instrumentation.pekko

import org.apache.pekko.actor.Actor
import org.apache.pekko.event.Logging.LogEvent
import org.apache.pekko.event.slf4j.Slf4jLogger

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
