/* ===================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.play.instrumentation

import org.aspectj.lang.annotation.{ Around, Pointcut, Aspect }
import org.aspectj.lang.ProceedingJoinPoint
import kamon.trace.TraceRecorder
import kamon.metrics.TraceMetrics.HttpClientRequest
import play.api.libs.ws.WSRequest
import scala.concurrent.Future
import play.api.libs.ws.WSResponse
import scala.util.{ Failure, Success }
import scala.concurrent.ExecutionContext.Implicits.global

@Aspect
class WSInstrumentation {

  @Pointcut("execution(* play.api.libs.ws.WS$WSRequest.execute()) && this(request)")
  def onExecuteRequest(request: WSRequest): Unit = {}

  @Around("onExecuteRequest(request)")
  def aroundExecuteRequest(pjp: ProceedingJoinPoint, request: WSRequest): Any = {
    import WSInstrumentation._

    val completionHandle = TraceRecorder.startSegment(HttpClientRequest(request.url, UserTime), basicRequestAttributes(request))

    val response = pjp.proceed().asInstanceOf[Future[WSResponse]]

    response.onComplete {
      case Failure(t) ⇒ completionHandle.map(_.finish(Map("completed-with-error" -> t.getMessage)))
      case Success(_) ⇒ completionHandle.map(_.finish(Map.empty))
    }

    response
  }
}

object WSInstrumentation {
  val UserTime = "UserTime"

  def basicRequestAttributes(request: WSRequest): Map[String, String] = {
    Map[String, String](
      "host" -> request.header("host").getOrElse("Unknown"),
      "path" -> request.method,
      "method" -> request.method)
  }
}

