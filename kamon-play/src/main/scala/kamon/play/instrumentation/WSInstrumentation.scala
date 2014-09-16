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

import kamon.Kamon
import kamon.metric.TraceMetrics.HttpClientRequest
import kamon.play.Play
import kamon.trace.TraceRecorder
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ Around, Aspect, Pointcut }
import play.api.libs.ws.ning.NingWSRequest
import play.api.libs.ws.{ WSRequest, WSResponse }

import scala.concurrent.Future

@Aspect
class WSInstrumentation {

  @Pointcut("execution(* play.api.libs.ws.ning.NingWSRequest.execute()) && this(request)")
  def onExecuteRequest(request: WSRequest): Unit = {}

  @Around("onExecuteRequest(request)")
  def aroundExecuteRequest(pjp: ProceedingJoinPoint, request: WSRequest): Any = {

    import kamon.play.instrumentation.WSInstrumentation._

    TraceRecorder.currentContext.map { ctx ⇒
      val executor = Kamon(Play)(ctx.system).defaultDispatcher
      val segmentHandle = TraceRecorder.startSegment(HttpClientRequest(request.url), basicRequestAttributes(request))
      val response = pjp.proceed().asInstanceOf[Future[WSResponse]]

      response.map(result ⇒ segmentHandle.map(_.finish()))(executor)
      response
    }.getOrElse(pjp.proceed())
  }
}

object WSInstrumentation {

  def uri(request: WSRequest): java.net.URI = request.asInstanceOf[NingWSRequest].builder.build().getURI

  def basicRequestAttributes(request: WSRequest): Map[String, String] = {
    Map[String, String](
      "host" -> uri(request).getHost,
      "path" -> uri(request).getPath,
      "method" -> request.method)
  }
}