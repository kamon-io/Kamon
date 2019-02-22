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

import io.netty.handler.codec.http.{HttpRequest, HttpResponse}
import kamon.context.Context
import kamon.play.{OperationNameFilter, instrumentation}
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._
import play.api.mvc.EssentialFilter

import scala.concurrent.Future

object NettyRequestHandlerInstrumentation {

  case class NettyGenericRequest(request: HttpRequest) extends GenericRequest {
    override val getHeader: String => Option[String] = (h: String) => Option(request.headers().get(h))
    override val method: String = request.method().name()
    override val url: String = request.uri()
    override val component = "play.server.netty"
  }

  case class NettyGenericResponse(response: HttpResponse) extends GenericResponse {
    override val statusCode: Int = response.status().code()
    override val reason: String = response.status.reasonPhrase()
  }

  implicit case object NettyGenericResponseBuilder extends GenericResponseBuilder[HttpResponse] {
    override def build(response: HttpResponse): GenericResponse = NettyGenericResponse(response)
  }

}

@Aspect
class NettyRequestHandlerInstrumentation {

  private lazy val filter: EssentialFilter = new OperationNameFilter()

  @Around("execution(* play.core.server.netty.PlayRequestHandler.handle(..)) && args(*, request)")
  def onHandle(pjp: ProceedingJoinPoint, request: HttpRequest): Any = {
    import NettyRequestHandlerInstrumentation._
    RequestHandlerInstrumentation.handleRequest(pjp.proceed().asInstanceOf[Future[HttpResponse]], NettyGenericRequest(request))
  }

  @Around("call(* play.api.http.HttpFilters.filters(..))")
  def filters(pjp: ProceedingJoinPoint): Any = {
    filter +: pjp.proceed().asInstanceOf[Seq[EssentialFilter]]
  }
}
