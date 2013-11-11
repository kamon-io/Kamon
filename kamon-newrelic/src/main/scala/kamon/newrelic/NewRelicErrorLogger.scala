package kamon.newrelic

import akka.actor.Actor
import akka.event.Logging.Error
import akka.event.Logging.{LoggerInitialized, InitializeLogger}
import com.newrelic.api.agent.NewRelic
import NewRelic.noticeError

class NewRelicErrorLogger extends Actor {
  def receive = {
    case InitializeLogger(_) => sender ! LoggerInitialized
    case error @ Error(cause, logSource, logClass, message) => notifyError(error)
    case anythingElse =>
  }

  def notifyError(error: Error): Unit = {
    noticeError(error.cause)
  }
}
