/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.play.instrumentation

import kamon.Kamon
import kamon.context.Context
import kamon.trace.Span
import kamon.util.CallingThreadExecutionContext

import scala.concurrent.Future

trait GenericRequest {
  val headers: Map[String, String]
  val method: String
  val url: String
  val component: String
}

trait GenericResponse {
  def statusCode: Int
  def reason: String
}

trait GenericResponseBuilder[T] {
  def build(response: T): GenericResponse
}

object RequestHandlerInstrumentation {

  def handleRequest[T](responseInvocation: => Future[T], request: GenericRequest)(implicit builder: GenericResponseBuilder[T]): Future[T] = {
    val incomingContext = context(request.headers)
    val serverSpan = Kamon.buildSpan("unknown-operation")
      .asChildOf(incomingContext.get(Span.ContextKey))
      .withMetricTag("span.kind", "server")
      .withMetricTag("component", request.component)
      .withMetricTag("http.method", request.method)
      .withTag("http.url", request.url)
      .start()

    val responseFuture = Kamon.withContext(incomingContext.withKey(Span.ContextKey, serverSpan))(responseInvocation)

    responseFuture.transform(
      s = response => {
        val genericResponse = builder.build(response)
        val statusCode = genericResponse.statusCode
        serverSpan.tag("http.status_code", statusCode)

        if(isError(statusCode)) {
          serverSpan.addError(genericResponse.reason)
        }

        if(statusCode == StatusCodes.NotFound)
          serverSpan.setOperationName("not-found")

        serverSpan.finish()
        response
      },
      f = error => {
        serverSpan.addError("error.object", error)
        serverSpan.finish()
        error
      }
    )(CallingThreadExecutionContext)
  }
}
