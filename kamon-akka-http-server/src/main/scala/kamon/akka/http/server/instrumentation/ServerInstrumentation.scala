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
package kamon.akka.http.server.instrumentation

import akka.http.scaladsl.Http.{ IncomingConnection, ServerBinding }
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpResponse, HttpRequest }
import akka.http.scaladsl.server.RequestContext
import kamon.Kamon
import kamon.akka.http.server.{ AkkaHttpServerExtension, AkkaHttpServer, AkkaHttpServerMetrics }
import kamon.metric.Entity
import kamon.trace.{ TraceContextAware, Tracer }
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class HttpRequestInstrumentation extends ServerInstrumentationUtils {

  @DeclareMixin("akka.http.scaladsl.model.HttpRequest")
  def mixinTraceContextAwareToRequest: TraceContextAware = TraceContextAware.default

  @Pointcut("execution(akka.http.scaladsl.model.HttpRequest.new(..)) && this(request)")
  def httpRequestCreation(request: HttpRequest with TraceContextAware): Unit = {}

  @After("httpRequestCreation(request)")
  def afterHttpRequestCreation(request: HttpRequest with TraceContextAware): Unit = {
    val akkaHttpServerExtension = Kamon(AkkaHttpServer)
    val incomingContext = Tracer.currentContext

    if (incomingContext.isEmpty) {
      val defaultTraceName = akkaHttpServerExtension.generateTraceName(request)
      val token = request.headers.find(_.name == akkaHttpServerExtension.settings.traceTokenHeaderName).map(_.value)

      val newContext = Kamon.tracer.newContext(defaultTraceName, token)
      Tracer.setCurrentContext(newContext)
    }

    request.traceContext

    recordAkkaHttpServerMetrics(request, akkaHttpServerExtension)
  }
}

@Aspect
class RequestContextInstrumentation extends ServerInstrumentationUtils {

  @DeclareMixin("akka.http.scaladsl.server.RequestContextImpl")
  def mixinTraceContextAwareToRequestContext: TraceContextAware = TraceContextAware.default

  @Pointcut("execution(akka.http.scaladsl.server.RequestContextImpl.new(..)) && this(context)")
  def requestContextCreation(context: RequestContext with TraceContextAware): Unit = {}

  @Around("requestContextCreation(context)")
  def afterCreation(pjp: ProceedingJoinPoint, context: RequestContext with TraceContextAware): Unit = {
    val incomingContext = Tracer.currentContext

    Tracer.withContext(incomingContext) {
      pjp.proceed()
    }
  }
}

@Aspect
class HttpResponseInstrumentation extends ServerInstrumentationUtils {

  @DeclareMixin("akka.http.scaladsl.model.HttpResponse")
  def mixinTraceContextAwareToResponse: TraceContextAware = TraceContextAware.default

  @Pointcut("call(akka.http.scaladsl.model.HttpResponse.new(..)) && !cflow(execution(* *..attachTraceTokenHeader(..)))")
  def httpResponseCreation(): Unit = {}

  @Around("httpResponseCreation()")
  def afterHttpResponseCreation(pjp: ProceedingJoinPoint) = {
    val akkaHttpServerExtension = Kamon(AkkaHttpServer)
    val incomingContext = Tracer.currentContext

    val response: HttpResponse = pjp.proceed().asInstanceOf[HttpResponse]

    val proceed =
      if (!incomingContext.isEmpty && akkaHttpServerExtension.settings.includeTraceTokenHeader) {
        val responseWithTraceTokenHeader =
          attachTraceTokenHeader(
            response,
            akkaHttpServerExtension.settings.traceTokenHeaderName,
            incomingContext.token)
        responseWithTraceTokenHeader
      } else {
        response
      }

    if (incomingContext.isOpen) {
      incomingContext.finish()
    }

    recordAkkaHttpServerMetrics(response, incomingContext.name, akkaHttpServerExtension)

    proceed
  }
}

@Aspect
class ServerInstrumentation {

  @Pointcut("execution(akka.http.scaladsl.Http.ServerBinding.new(..)) && this(binding)")
  def serverBindingCreation(binding: ServerBinding): Unit = {}

  @After("serverBindingCreation(binding)")
  def afterServerBindingCreation(binding: ServerBinding): Unit = {
    val akkaHttpServerExtension = Kamon(AkkaHttpServer)
    val serverMetrics = akkaHttpServerExtension.serverMetrics
    val bindingAddress = binding.localAddress.toString
  }

  @Pointcut("execution(akka.http.scaladsl.Http.IncomingConnection.new(..)) && this(connection)")
  def newIncomingConnection(connection: IncomingConnection): Unit = {}

  @After("newIncomingConnection(connection)")
  def afterNewIncomingConnection(connection: IncomingConnection): Unit = {
    val bindingAddress = connection.localAddress.toString
    val akkaHttpServerExtension = Kamon(AkkaHttpServer)
    val serverMetrics = akkaHttpServerExtension.serverMetrics

    if (Kamon.metrics.shouldTrack(bindingEntity(bindingAddress))) {
      serverMetrics.recordConnection(bindingAddress)
    }
  }

  private def bindingEntity(name: String): Entity =
    Entity(name, AkkaHttpServerMetrics.category)
}

trait ServerInstrumentationUtils {
  def recordAkkaHttpServerMetrics(request: HttpRequest, akkaHttpServerExtension: AkkaHttpServerExtension): Unit =
    akkaHttpServerExtension.serverMetrics.openConnections.increment()

  def recordAkkaHttpServerMetrics(response: HttpResponse, traceName: String, akkaHttpServerExtension: AkkaHttpServerExtension): Unit = {
    akkaHttpServerExtension.serverMetrics.recordResponse(traceName, response.status.intValue().toString)
    akkaHttpServerExtension.serverMetrics.openConnections.decrement()
  }

  def attachTraceTokenHeader(response: HttpResponse, traceTokenHeaderName: String, traceToken: String): HttpResponse =
    response.withHeaders(response.headers.filterNot(_.name == traceTokenHeaderName) :+ RawHeader(traceTokenHeaderName, traceToken))
}

object ServerInstrumentationUtils extends ServerInstrumentationUtils
