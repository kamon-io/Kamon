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
import com.newrelic.agent.errors.{ ThrowableError }
import com.newrelic.agent.service.ServiceFactory
import kamon.trace.{ Tracer, TraceContextAware }
import scala.util.control.NoStackTrace

trait CustomParamsSupport {
  this: NewRelicErrorLogger ⇒

  def customParams: Map[String, String]
}

class NewRelicErrorLogger extends Actor with ActorLogging with CustomParamsSupport {
  override def customParams: Map[String, String] = Map.empty

  def receive = {
    case InitializeLogger(_)                                ⇒ sender ! LoggerInitialized
    case error @ Error(cause, logSource, logClass, message) ⇒ notifyError(error)
    case anythingElse                                       ⇒
  }

  def notifyError(error: Error): Unit = {
    val params = new util.HashMap[String, String]()
    customParams foreach { case (k, v) ⇒ params.put(k, v) }

    params.put("LogSource", error.logSource)
    params.put("LogClass", error.logClass.toString)
    error match {
      case e: TraceContextAware ⇒ params.put("TraceToken", e.traceContext.token)
      case _                    ⇒
    }

    if (error.cause == Error.NoCause)
      reportError(loggedMessage(error.message.toString, params))
    else
      reportError(loggedException(error.message.toString, error.cause, params))
  }

  def loggedMessage(message: String, params: util.HashMap[String, String]) =
    loggedException("", LoggedMessage(message), params)

  def loggedException(message: String, cause: Throwable, params: util.HashMap[String, String]) = {
    if (null != message && message.length > 0) params.put("ErrorMessage", message)
    val uri = s"/${Tracer.currentContext.name}"
    val transaction = s"WebTransaction/Uri$uri"
    LoggedException(cause, params, transaction, uri);
  }

  def reportError(error: ThrowableError) = ServiceFactory.getRPMService().getErrorService().reportError(error)

}

case class LoggedMessage(message: String) extends Throwable(message) with NoStackTrace

case class LoggedException(cause: Throwable, params: util.HashMap[String, String], transaction: String, uri: String)
  extends ThrowableError(null, transaction, cause, uri, System.currentTimeMillis(), null, null, null, params, null)
