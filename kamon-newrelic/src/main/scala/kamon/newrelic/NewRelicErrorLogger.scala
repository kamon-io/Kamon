/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.newrelic

import akka.actor.{ActorLogging, Actor}
import akka.event.Logging.Error
import akka.event.Logging.{ LoggerInitialized, InitializeLogger }
import com.newrelic.api.agent.{ NewRelic ⇒ NR }
import kamon.trace.TraceContextAware

class NewRelicErrorLogger extends Actor with ActorLogging {
  var aspectJMissingAlreadyReported = false

  def receive = {
    case InitializeLogger(_)                                ⇒ sender ! LoggerInitialized
    case error @ Error(cause, logSource, logClass, message) ⇒ notifyError(error)
    case anythingElse                                       ⇒
  }

  def notifyError(error: Error): Unit = {
    val params = new java.util.HashMap[String, String]()

    if (error.isInstanceOf[TraceContextAware]) {
      val ctx = error.asInstanceOf[TraceContextAware].traceContext

      for (c ← ctx) {
        params.put("TraceToken", c.token)
      }
    } else if (! aspectJMissingAlreadyReported) {
      log.warning("ASPECTJ WEAVER MISSING. You might have missed to include the java agent")
      aspectJMissingAlreadyReported = true
    }

    if (error.cause == Error.NoCause) {
      NR.noticeError(error.message.toString, params)
    } else {
      NR.noticeError(error.cause, params)
    }

  }
}
