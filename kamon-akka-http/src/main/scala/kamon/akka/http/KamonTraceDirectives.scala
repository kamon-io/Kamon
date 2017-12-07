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
package kamon.akka.http

import akka.http.scaladsl.model.{HttpHeader, HttpRequest}
import akka.http.scaladsl.server.{Directive, RouteResult}
import kamon.Kamon
import kamon.context.{Context, TextMap}
import kamon.trace.Span
import kamon.util.CallingThreadExecutionContext

import scala.util.{Failure, Success}

trait KamonDirectives {

  def trace: Directive[Unit] = traceDirective
  def context: Directive[Unit] = contextDirective

  private val traceDirective: Directive[Unit] = Directive[Unit] {
    innerRoute => requestContext => {
      val incomingContext = decodeContext(requestContext.request)
      val serverSpan = Kamon.buildSpan(requestContext.request.uri.path.toString)
        .asChildOf(incomingContext.get(Span.ContextKey))
        .withMetricTag("span.kind", "server")
        .withTag("akka.http.server.method", requestContext.request.method.value)
        .withTag("akka.http.server.url", requestContext.request.uri.toString())
        .start()

      Kamon.withContext(incomingContext.withKey(Span.ContextKey, serverSpan)) {
        val innerRouteResult = innerRoute()(requestContext)
        innerRouteResult.onComplete(res => {
          res match {
            case Success(routeResult) => routeResult match {
              case RouteResult.Complete(httpResponse) =>
                if(httpResponse.status.isFailure()) serverSpan.tag("error", true)
              case RouteResult.Rejected(rejections) =>
                val reason = if(rejections.isEmpty) "not-found" else "rejected"
                serverSpan.setOperationName(reason)
                serverSpan.addError(reason)
            }
            case Failure(throwable) =>
              serverSpan.addError(throwable.getMessage)
          }

          serverSpan.finish()
        })(CallingThreadExecutionContext)

        innerRouteResult
      }
    }
  }

  private val contextDirective: Directive[Unit] = Directive[Unit] {
    innerRoute => requestContext => {
      val incomingContext = decodeContext(requestContext.request)

      Kamon.withContext(incomingContext) {
        innerRoute()(requestContext)
      }
    }
  }

  private def decodeContext(request: HttpRequest): Context = {
    val headersTextMap = readOnlyTextMapFromHeaders(request.headers)
    Kamon.contextCodec().HttpHeaders.decode(headersTextMap)
  }

  private def readOnlyTextMapFromHeaders(headers: Seq[HttpHeader]): TextMap = new TextMap {
    private val headersMap = headers.map { h => h.name -> h.value }.toMap

    override def put(key: String, value: String): Unit = {}
    override def get(key: String): Option[String] = headersMap.get(key)
    override def values: Iterator[(String, String)] = headersMap.iterator
  }
}