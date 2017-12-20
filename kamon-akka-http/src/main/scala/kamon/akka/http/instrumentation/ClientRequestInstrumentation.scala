/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package akka.kamon.http.instrumentation

import akka.dispatch.ExecutionContexts
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpHeader, HttpMessage, HttpRequest, HttpResponse}
import kamon.Kamon
import kamon.akka.http.ClientInstrumentationLevel.RequestLevelAPI
import kamon.akka.http.{AkkaHttpServerMetrics, ClientInstrumentationLevel}
import kamon.trace._
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

import scala.concurrent.Future
import scala.util._

@Aspect
class ClientRequestInstrumentation {

  private def componentPrefixed(metricName: String) = s"akka.http.server.$metricName"

  @Around("execution(* akka.http.scaladsl.HttpExt.singleRequest(..)) && args(request, *, *, *, *)")
  def onSingleRequest(pjp: ProceedingJoinPoint, request: HttpRequest): Any = {
    val settings = AkkaHttpServerMetrics.settings

    val span = settings.clientInstrumentationLevel match  {
      case RequestLevelAPI =>
        val operationName = AkkaHttpServerMetrics.generateRequestLevelApiSegmentName(request)
        Kamon.buildSpan(operationName).start()
      case _ =>
        Kamon.currentContext().get(Span.ContextKey)
    }

    span
      .tag(componentPrefixed("url"), request.getUri().toString)
      .tag(componentPrefixed("method"), request.method.value)

    val responseFuture = Kamon.withContext(Kamon.currentContext().withKey(Span.ContextKey, span)) {
      pjp.proceed().asInstanceOf[Future[HttpResponse]]
    }

    responseFuture.onComplete {
      case Success(_) ⇒ span.finish()
      case Failure(t) ⇒ span.addError(t.getMessage, t)
    }(ExecutionContexts.sameThreadExecutionContext)

    responseFuture
  }

  @Around("execution(* akka.http.scaladsl.model.HttpMessage.withDefaultHeaders(*)) && this(request) && args(defaultHeaders)")
  def onWithDefaultHeaders(pjp: ProceedingJoinPoint, request: HttpMessage, defaultHeaders: List[HttpHeader]): Any = {
    val modifiedHeaders = Kamon.contextCodec().HttpHeaders.encode(Kamon.currentContext()).values.toVector.map(c => RawHeader(c._1, c._2))
    val headers = modifiedHeaders ++ defaultHeaders.toSeq
    pjp.proceed(Array[AnyRef](request, headers))
  }
}