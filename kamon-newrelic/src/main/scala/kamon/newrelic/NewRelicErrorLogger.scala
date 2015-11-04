/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.newrelic

import java.util
import akka.actor.{ Actor, ActorLogging }
import akka.event.Logging.{ Error, InitializeLogger, LoggerInitialized }
import com.newrelic.agent.errors.{ ErrorService, ThrowableError }
import com.newrelic.agent.service.ServiceFactory
import kamon.trace.{ Tracer, TraceContextAware }
import scala.util.Try
import scala.util.control.NoStackTrace

trait CustomParamsSupport {
  this: NewRelicErrorLogger ⇒

  def customParams: Map[String, String]
}

class NewRelicErrorLogger extends Actor with ActorLogging with CustomParamsSupport {
  override def customParams: Map[String, String] = Map.empty
  private val errorService: Option[ErrorService] = Try(ServiceFactory.getRPMService).map(_.getErrorService).toOption

  if (errorService.isEmpty)
    log.warning("Not sending errors to New Relic as the New Relic Agent is not started")

  def receive = {
    case InitializeLogger(_)                                ⇒ sender ! LoggerInitialized
    case error @ Error(cause, logSource, logClass, message) ⇒ notifyError(error)
    case anythingElse                                       ⇒
  }

  def notifyError(error: Error): Unit = {
    val params = new util.HashMap[String, String]()
    customParams foreach { case (k, v) ⇒ params.put(k, v) }

    params.put("LogSource", error.logSource)
    params.put("LogClass", error.logClass.getCanonicalName)
    error match {
      case e: TraceContextAware if e.traceContext.token.length > 0 ⇒
        params.put("TraceToken", e.traceContext.token)
      case _ ⇒
    }

    if (error.cause == Error.NoCause)
      reportError(loggedMessage(error.message, params))
    else
      reportError(loggedException(error.message, error.cause, params))
  }

  def loggedMessage(message: Any, params: util.HashMap[String, String]) =
    loggedException(null, LoggedMessage(message), params)

  def loggedException(message: Any, cause: Throwable, params: util.HashMap[String, String]) = {
    if (Option(message).isDefined) params.put("ErrorMessage", message.toString)
    val uri = s"/${Tracer.currentContext.name}"
    val transaction = s"WebTransaction/Uri$uri"
    LoggedException(cause, params, transaction, uri)
  }

  def reportError(error: ThrowableError) = errorService.foreach(_.reportError(error))
}

case class LoggedMessage(message: Any) extends Throwable(message match { case null ⇒ "" case s ⇒ s.toString }) with NoStackTrace

case class LoggedException(cause: Throwable, params: util.HashMap[String, String], transaction: String, uri: String)
    extends ThrowableError(null, transaction, cause, uri, System.currentTimeMillis(), null, null, null, params, null) {

  //ThrowableError has some funky equals method which gives false positives in tests
  override def equals(any: Any): Boolean = {
    any match {
      case that: LoggedException ⇒
        that.cause == this.cause && that.params == this.params && that.transaction == this.transaction && that.uri == this.uri
      case _ ⇒ false
    }
  }
  //
  override def toString = s"${this.getClass.getSimpleName}: cause=$cause transaction=$transaction uri=$uri params=$params"
}
