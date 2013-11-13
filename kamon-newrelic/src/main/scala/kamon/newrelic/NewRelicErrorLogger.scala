package kamon.newrelic

import akka.actor.Actor
import akka.event.Logging.Error
import akka.event.Logging.{LoggerInitialized, InitializeLogger}
import com.newrelic.api.agent.NewRelic
import kamon.trace.ContextAware

class NewRelicErrorLogger extends Actor {
  def receive = {
    case InitializeLogger(_) => sender ! LoggerInitialized
    case error @ Error(cause, logSource, logClass, message) => notifyError(error)
    case anythingElse =>
  }

  def notifyError(error: Error): Unit = {
    val params = new java.util.HashMap[String, String]()
    val ctx = error.asInstanceOf[ContextAware].traceContext

    for(c <- ctx) {
      params.put("UOW", c.uow)
    }

    NewRelic.noticeError(error.cause, params)
  }
}
