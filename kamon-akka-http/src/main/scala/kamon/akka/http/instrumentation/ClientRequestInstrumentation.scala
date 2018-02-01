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

package akka.http.impl.engine.client

import akka.dispatch.ExecutionContexts
import akka.http.impl.engine.client.PoolInterfaceActor.PoolRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import kamon.Kamon
import kamon.akka.http.AkkaHttp
import kamon.context.HasContext
import kamon.trace.SpanCustomizer
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

import scala.concurrent.Future
import scala.util._

@Aspect
class ClientRequestInstrumentation {

  @Around("execution(* akka.http.scaladsl.HttpExt.singleRequest(..)) && args(request, *, *, *)")
  def onSingleRequest(pjp: ProceedingJoinPoint, request: HttpRequest): Any = {
    val operationName = AkkaHttp.clientOperationName(request)
    val customizer = Kamon.currentContext().get(SpanCustomizer.ContextKey)
    val span = customizer.customize(
      Kamon.buildSpan(operationName)
        .withTag("component", "akka.http.client")
        .withTag("span.kind", "client")
        .withTag("http.url", request.getUri().toString)
        .withTag("http.method", request.method.value)
    ).start()

    val responseFuture = Kamon.withSpan(span, finishSpan = false) {
      pjp.proceed().asInstanceOf[Future[HttpResponse]]
    }

    responseFuture.onComplete {
      case Success(response) ⇒ {
        val status = response.status
        if(status.isFailure())
          span.addError(status.reason())

        val spanWithStatusTag = if (AkkaHttp.addHttpStatusCodeAsMetricTag) {
          span.tagMetric("http.status_code", status.intValue.toString())
        } else {
          span.tag("http.status_code", status.intValue())
        }

        spanWithStatusTag.finish()
      }
      case Failure(t) ⇒ {
        span.addError(t.getMessage, t).finish()
      }
    }(ExecutionContexts.sameThreadExecutionContext)

    responseFuture
  }


  @DeclareMixin("akka.http.impl.engine.client.PoolInterfaceActor.PoolRequest")
  def mixinContextIntoPoolRequest(): HasContext = HasContext.fromCurrentContext()

  @After("execution(akka.http.impl.engine.client.PoolInterfaceActor.PoolRequest.new(..)) && this(poolRequest)")
  def afterPoolRequestConstructor(poolRequest: HasContext): Unit = {
    // Initialize the Context in the Thread creating the PoolRequest
    poolRequest.context
  }

  @Around("execution(* akka.http.impl.engine.client.PoolInterfaceActor.dispatchRequest(..)) && args(poolRequest)")
  def aroundDispatchRequest(pjp: ProceedingJoinPoint, poolRequest: PoolRequest with HasContext): Any = {
    val contextHeaders = Kamon.contextCodec().HttpHeaders.encode(poolRequest.context).values.map(c => RawHeader(c._1, c._2))
    val requestWithContext = poolRequest.request.withHeaders(poolRequest.request.headers ++ contextHeaders)

    pjp.proceed(Array(poolRequest.copy(request = requestWithContext)))
  }
}