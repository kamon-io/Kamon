package kamon.logging

import akka.actor.Actor
import kamon.{Tracer, Kamon}
import akka.event.{BusLogging, LogSource, LoggingAdapter}

trait UowActorLogging {
  this: Actor =>

  val log = {
    val (str, clazz) = LogSource(this, context.system)
    new BusLogging(context.system.eventStream, str, clazz) with UowLoggingAdapter
  }

}

trait UowLoggingAdapter extends LoggingAdapter {
  def currentUow: String = Tracer.context().flatMap(_.userContext).map(_.toString).getOrElse("")
  protected abstract override def notifyWarning(message: String)  = super.notifyWarning(s"[${currentUow}] - $message")
  protected abstract override def notifyInfo(message: String)     = super.notifyInfo(   s"[${currentUow}] - $message")
  protected abstract override def notifyDebug(message: String)    = super.notifyDebug(  s"[${currentUow}] - $message")
  protected abstract override def notifyError(message: String)    = super.notifyError(  s"[${currentUow}] - $message")
  protected abstract override def notifyError(cause: Throwable, message: String) = super.notifyError(cause, s"[${currentUow}] - $message")
}
