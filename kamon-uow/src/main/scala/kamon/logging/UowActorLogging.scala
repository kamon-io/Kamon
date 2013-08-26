package kamon.logging

import akka.actor.{ActorSystem, Actor}
import kamon.{Tracer}
import akka.event.{LoggingBus, LogSource, LoggingAdapter}
import akka.event.Logging._
import akka.event.slf4j.{Logger, SLF4JLogging}
import akka.event.Logging.Info
import akka.event.Logging.Warning
import akka.event.Logging.Error
import akka.event.Logging.Debug
import org.slf4j.MDC
import akka.util.Helpers

trait UowActorLogging {
  this: Actor =>

  val log = {
    val (str, clazz) = LogSource(this, context.system)
    new ExtendedBusLogging(context.system.eventStream, str, clazz)
  }
}

trait UowLogging {
  self: Any =>
  def system: ActorSystem

  val log = {
    val (str, clazz) = LogSource(self.getClass, system)
    new ExtendedBusLogging(system.eventStream, str, clazz)
  }
}

class ExtendedBusLogging(val bus: LoggingBus, val logSource: String, val logClass: Class[_]) extends LoggingAdapter {

  import akka.event.Logging._

  def isErrorEnabled = bus.logLevel >= ErrorLevel
  def isWarningEnabled = bus.logLevel >= WarningLevel
  def isInfoEnabled = bus.logLevel >= InfoLevel
  def isDebugEnabled = bus.logLevel >= DebugLevel

  def currentUow: String = Tracer.context().flatMap(_.userContext).map(_.toString).getOrElse("")
  def extras = Map("uow" -> currentUow)

  protected def notifyError(message: String): Unit = bus.publish(Error(logSource, logClass, RichLogEvent(message, extras)))
  protected def notifyError(cause: Throwable, message: String): Unit = bus.publish(Error(cause, logSource, logClass, RichLogEvent(message, extras)))
  protected def notifyWarning(message: String): Unit = bus.publish(Warning(logSource, logClass, RichLogEvent(message, extras)))
  protected def notifyInfo(message: String): Unit = bus.publish(Info(logSource, logClass, RichLogEvent(message, extras)))
  protected def notifyDebug(message: String): Unit = bus.publish(Debug(logSource, logClass, RichLogEvent(message, extras)))
}

case class RichLogEvent(message: String, extras: Map[String, Any])



class ExtendedSlf4jLogger extends Actor with SLF4JLogging {

  val mdcThreadAttributeName = "sourceThread"
  val mdcAkkaSourceAttributeName = "akkaSource"
  val mdcAkkaTimestamp = "akkaTimestamp"

  def receive = {

    case event @ Error(cause, logSource, logClass, message) ⇒
      withMdc(logSource, event) {
        cause match {
          case Error.NoCause | null ⇒ withRichEventProcessing(message) { Logger(logClass, logSource).error(if (message != null) message.toString else null) }
          case cause                ⇒ withRichEventProcessing(message) { Logger(logClass, logSource).error(if (message != null) message.toString else cause.getLocalizedMessage, cause) }
        }
      }

    case event @ Warning(logSource, logClass, message) ⇒
      withMdc(logSource, event) { withRichEventProcessing(message) { Logger(logClass, logSource).warn("{}", message.asInstanceOf[AnyRef]) } }

    case event @ Info(logSource, logClass, message) ⇒
      withMdc(logSource, event) { withRichEventProcessing(message) { Logger(logClass, logSource).info("{}", message.asInstanceOf[AnyRef]) } }

    case event @ Debug(logSource, logClass, message) ⇒
      withMdc(logSource, event) { withRichEventProcessing(message) { Logger(logClass, logSource).debug("{}", message.asInstanceOf[AnyRef]) } }

    case InitializeLogger(_) ⇒
      log.info("Slf4jLogger started")
      sender ! LoggerInitialized
  }

  def withRichEventProcessing(message: Any)(delegate: => Unit): Unit = message match {
    case RichLogEvent(event, extras) => {
      extras.foreach { case (k, v) => MDC.put(k, v.toString) }
      delegate
      MDC.clear()
    }
    case _ => delegate
  }

  @inline
  final def withMdc(logSource: String, logEvent: LogEvent)(logStatement: ⇒ Unit) {
    MDC.put(mdcAkkaSourceAttributeName, logSource)
    MDC.put(mdcThreadAttributeName, logEvent.thread.getName)
    MDC.put(mdcAkkaTimestamp, formatTimestamp(logEvent.timestamp))
    try logStatement finally {
      MDC.remove(mdcAkkaSourceAttributeName)
      MDC.remove(mdcThreadAttributeName)
      MDC.remove(mdcAkkaTimestamp)
    }
  }

  /**
   * Override this method to provide a differently formatted timestamp
   * @param timestamp a "currentTimeMillis"-obtained timestamp
   * @return the given timestamp as a UTC String
   */
  protected def formatTimestamp(timestamp: Long): String =
    Helpers.currentTimeMillisToUTCString(timestamp)
}
