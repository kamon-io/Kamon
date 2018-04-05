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

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import kamon.context.Context
import kamon.play.{OperationNameFilter, instrumentation}
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._
import play.api.mvc.EssentialFilter

import scala.concurrent.Future

object AkkaHttpRequestHandlerInstrumentation {

  case class AkkaHttpGenericRequest(request: HttpRequest) extends GenericRequest {
    override val headers: Map[String, String] = request.headers.map { h => h.name() -> h.value() }.toMap
    override val method: String = request.method.value
    override val url: String = request.getUri.toString
    override val spanKind: String = "play.server"
  }

  case class AkkaHttpGenericResponse(response: HttpResponse) extends GenericResponse {
    override val statusCode: Int = response.status.intValue()
    override val reason: String = response.status.reason()
  }

  implicit case object AkkaHttpGenericResponseBuilder extends GenericResponseBuilder[HttpResponse] {
    override def build(response: HttpResponse): GenericResponse = AkkaHttpGenericResponse(response)
  }

}

@Aspect
class AkkaHttpRequestHandlerInstrumentation {

  private lazy val filter: EssentialFilter = new OperationNameFilter()

  @Around("execution(* play.core.server.AkkaHttpServer.handleRequest(..)) && args(request, *)")
  def routeRequestNumberTwo(pjp: ProceedingJoinPoint, request: HttpRequest): Any = {
    import AkkaHttpRequestHandlerInstrumentation._
    RequestHandlerInstrumentation.handleRequest(pjp.proceed().asInstanceOf[Future[HttpResponse]], AkkaHttpGenericRequest(request))
  }

  @Around("call(* play.api.http.HttpFilters.filters(..))")
  def filters(pjp: ProceedingJoinPoint): Any = {
    filter +: pjp.proceed().asInstanceOf[Seq[EssentialFilter]]
  }
}
