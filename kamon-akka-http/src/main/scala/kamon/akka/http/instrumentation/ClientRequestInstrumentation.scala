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

package kamon.akka.http.instrumentation

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpHeader, HttpMessage, HttpRequest, HttpResponse }
import kamon.akka.http.{ AkkaHttpExtension, ClientInstrumentationLevel }
import kamon.trace._
import kamon.util.SameThreadExecutionContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

import scala.concurrent.Future
import scala.util._

@Aspect
class ClientRequestInstrumentation {

  @Around("execution(* akka.http.scaladsl.HttpExt.singleRequest(..)) && args(request, *, *, *, *)")
  def onSingleRequest(pjp: ProceedingJoinPoint, request: HttpRequest): Any = {
    Tracer.currentContext.collect { ctx ⇒
      val segment =
        if (AkkaHttpExtension.settings.clientInstrumentationLevel == ClientInstrumentationLevel.RequestLevelAPI) {
          val segmentName = AkkaHttpExtension.generateRequestLevelApiSegmentName(request)
          ctx.startSegment(segmentName, SegmentCategory.HttpClient, AkkaHttpExtension.SegmentLibraryName)
        } else EmptyTraceContext.EmptySegment

      val responseFuture = pjp.proceed().asInstanceOf[Future[HttpResponse]]
      responseFuture.onComplete {
        case Success(_) ⇒ segment.finish()
        case Failure(t) ⇒ segment.finishWithError(t)
      }(SameThreadExecutionContext)
      responseFuture
    } getOrElse pjp.proceed()
  }

  @Around("execution(* akka.http.scaladsl.model.HttpMessage.withDefaultHeaders(*)) && this(request) && args(defaultHeaders)")
  def onWithDefaultHeaders(pjp: ProceedingJoinPoint, request: HttpMessage, defaultHeaders: List[HttpHeader]): Any = {
    val modifiedHeaders = Tracer.currentContext.collect { ctx ⇒
      if (AkkaHttpExtension.settings.includeTraceTokenHeader) RawHeader(AkkaHttpExtension.settings.traceTokenHeaderName, ctx.token) :: defaultHeaders
      else defaultHeaders
    } getOrElse defaultHeaders

    pjp.proceed(Array[AnyRef](request, modifiedHeaders))
  }
}