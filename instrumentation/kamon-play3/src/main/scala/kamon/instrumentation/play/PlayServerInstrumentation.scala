/*
 * Copyright 2013-2025 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.play

import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

import com.typesafe.config.Config
import io.netty.channel.Channel
import io.netty.handler.codec.http.{HttpRequest, HttpResponse}
import io.netty.util.concurrent.GenericFutureListener
import kamon.{ClassLoading, Kamon}
import kamon.context.Storage
import kamon.instrumentation.pekko.http.{ServerFlowWrapper => PekkoHttpServerFlowWrapper}
import kamon.instrumentation.context.HasTimestamp
import kamon.instrumentation.http.HttpServerInstrumentation.RequestHandler
import kamon.instrumentation.http.{HttpMessage, HttpServerInstrumentation}
import kamon.util.CallingThreadExecutionContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.api.instrumentation.mixin.Initializer
import kanela.agent.libs.net.bytebuddy.asm.Advice
import org.slf4j.LoggerFactory
import play.api.mvc.RequestHeader
import play.api.routing.Router
import play.core.server.NettyServer

import scala.annotation.static
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.{Failure, Success}

class PlayServerInstrumentation extends InstrumentationBuilder {

  /**
    * When using the Pekko HTTP server, we will use the exact same instrumentation that comes from the Pekko HTTP module,
    * the only difference here is that we will change the component name.
    */
  onType("play.core.server.PekkoHttpServer")
    .when(classIsPresent("play.core.server.PekkoHttpServerProvider"))
    .advise(
      anyMethods("createServerBinding", "play$core$server$PekkoHttpServer$$createServerBinding"),
      classOf[CreatePekkoHttpServerBindingAdvice]
    )

  /**
    * When using the Netty HTTP server we are rolling our own instrumentation which simply requires us to create the
    * HttpServerInstrumentation instance and call the expected callbacks on it.
    */
  onType("play.core.server.NettyServer")
    .when(classIsPresent("play.core.server.NettyServerProvider"))
    .mixin(classOf[HasServerInstrumentation.Mixin])
    .advise(isConstructor, classOf[NettyServerInitializationAdvice])

  if (hasGenericFutureListener()) {
    onType("play.core.server.netty.PlayRequestHandler")
      .when(classIsPresent("play.core.server.NettyServerProvider"))
      .mixin(classOf[HasServerInstrumentation.Mixin])
      .mixin(classOf[HasTimestamp.Mixin])
      .advise(isConstructor, classOf[PlayRequestHandlerConstructorAdvice])
      .advise(method("handle"), classOf[NettyPlayRequestHandlerHandleAdvice])
  }

  /**
    * This final bit ensures that we will apply an operation name right before filters get to execute.
    */
  onType("play.api.http.DefaultHttpRequestHandler")
    .advise(method("filterHandler").and(takesArguments(2)), classOf[GenerateOperationNameOnFilterHandler])

  private def hasGenericFutureListener(): Boolean = {
    try { Class.forName("io.netty.util.concurrent.GenericFutureListener") != null }
    catch { case _: Throwable => false }
  }

}

class CreatePekkoHttpServerBindingAdvice
object CreatePekkoHttpServerBindingAdvice {

  @Advice.OnMethodEnter
  @static def enter(): Unit =
    PekkoHttpServerFlowWrapper.changeSettings("play.server.pekko-http", "kamon.instrumentation.play.http.server")

  @Advice.OnMethodExit
  @static def exit(): Unit =
    PekkoHttpServerFlowWrapper.resetSettings()

}

class NettyServerInitializationAdvice
object NettyServerInitializationAdvice {

  @Advice.OnMethodExit
  @static def exit(@Advice.This server: NettyServer): Unit = {
    val serverWithInstrumentation = server.asInstanceOf[HasServerInstrumentation]
    val config = Kamon.config().getConfig("kamon.instrumentation.play.http.server")
    val instrumentation = HttpServerInstrumentation.from(
      config,
      component = "play.server.netty",
      interface = server.mainAddress.getHostName,
      port = server.mainAddress.getPort
    )

    serverWithInstrumentation.setServerInstrumentation(instrumentation)
  }
}

class NettyPlayRequestHandlerHandleAdvice
object NettyPlayRequestHandlerHandleAdvice {

  @Advice.OnMethodEnter
  @static def enter(
    @Advice.This requestHandler: Any,
    @Advice.Argument(0) channel: Channel,
    @Advice.Argument(1) request: HttpRequest
  ): RequestProcessingContext = {
    val playRequestHandler = requestHandler.asInstanceOf[HasServerInstrumentation]
    val serverInstrumentation = playRequestHandler.serverInstrumentation()

    val serverRequestHandler = serverInstrumentation.createHandler(
      request = toRequest(request, serverInstrumentation.interface(), serverInstrumentation.port()),
      deferSamplingDecision = true
    )

    if (!playRequestHandler.hasBeenUsedBefore()) {
      playRequestHandler.markAsUsed()
      channel.closeFuture().addListener(new GenericFutureListener[io.netty.util.concurrent.Future[_ >: Void]] {
        override def operationComplete(future: io.netty.util.concurrent.Future[_ >: Void]): Unit = {
          val connectionEstablishedTime =
            Kamon.clock().toInstant(playRequestHandler.asInstanceOf[HasTimestamp].timestamp)
          val aliveTime = Duration.between(connectionEstablishedTime, Kamon.clock().instant())
          serverInstrumentation.connectionClosed(aliveTime, playRequestHandler.handledRequests())
        }
      })
    }

    playRequestHandler.requestHandled()
    RequestProcessingContext(serverRequestHandler, Kamon.storeContext(serverRequestHandler.context))
  }

  @Advice.OnMethodExit
  @static def exit(
    @Advice.Enter rpContext: RequestProcessingContext,
    @Advice.Return result: scala.concurrent.Future[HttpResponse]
  ): Unit = {
    val reqHandler = rpContext.requestHandler

    result.onComplete {
      case Success(value) =>
        reqHandler.buildResponse(toResponse(value), rpContext.scope.context)
        reqHandler.responseSent()

      case Failure(exception) =>
        reqHandler.span.fail(exception)
        reqHandler.responseSent()

    }(CallingThreadExecutionContext)

    rpContext.scope.close()
  }

  case class RequestProcessingContext(requestHandler: RequestHandler, scope: Storage.Scope)

  private def toRequest(request: HttpRequest, serverHost: String, serverPort: Int): HttpMessage.Request =
    new HttpMessage.Request {
      override def url: String = request.uri()
      override def path: String = request.uri()
      override def method: String = request.method().name()
      override def host: String = serverHost
      override def port: Int = serverPort

      override def read(header: String): Option[String] =
        Option(request.headers().get(header))

      override def readAll(): Map[String, String] =
        request.headers().entries().asScala.map(e => e.getKey -> e.getValue).toMap
    }

  private def toResponse(response: HttpResponse): HttpMessage.ResponseBuilder[HttpResponse] =
    new HttpMessage.ResponseBuilder[HttpResponse] {
      override def build(): HttpResponse =
        response

      override def statusCode: Int =
        response.status().code()

      override def write(header: String, value: String): Unit =
        response.headers().add(header, value)
    }
}

class PlayRequestHandlerConstructorAdvice
object PlayRequestHandlerConstructorAdvice {

  @Advice.OnMethodExit
  @static def exit(@Advice.This playRequestHandler: HasServerInstrumentation, @Advice.Argument(0) server: Any): Unit = {
    val instrumentation = server.asInstanceOf[HasServerInstrumentation].serverInstrumentation()
    playRequestHandler.asInstanceOf[HasTimestamp].setTimestamp(System.nanoTime())
    playRequestHandler.setServerInstrumentation(instrumentation)
    instrumentation.connectionOpened()
  }
}

trait HasServerInstrumentation {
  def serverInstrumentation(): HttpServerInstrumentation
  def setServerInstrumentation(serverInstrumentation: HttpServerInstrumentation): Unit
  def hasBeenUsedBefore(): Boolean
  def markAsUsed(): Unit
  def requestHandled(): Unit
  def handledRequests(): Long
}

object HasServerInstrumentation {

  class Mixin extends HasServerInstrumentation {
    private var _serverInstrumentation: HttpServerInstrumentation = null
    private var _hasBeenUsedBefore: Boolean = false
    private var _handledRequests: AtomicLong = null

    override def serverInstrumentation(): HttpServerInstrumentation =
      this._serverInstrumentation

    override def setServerInstrumentation(serverInstrumentation: HttpServerInstrumentation): Unit =
      this._serverInstrumentation = serverInstrumentation

    override def hasBeenUsedBefore(): Boolean =
      this._hasBeenUsedBefore

    override def markAsUsed(): Unit =
      this._hasBeenUsedBefore = true

    override def requestHandled(): Unit =
      this._handledRequests.incrementAndGet()

    override def handledRequests(): Long =
      this._handledRequests.get()

    @Initializer
    def init(): Unit = {
      _handledRequests = new AtomicLong()
    }
  }
}

class GenerateOperationNameOnFilterHandler
object GenerateOperationNameOnFilterHandler {

  private val defaultRouterNameGenerator = new DefaultRouterOperationNameGenerator()
  private val _logger = LoggerFactory.getLogger(GenerateOperationNameOnFilterHandler.getClass)

  @volatile private var _routerNameGenerator: RouterOperationNameGenerator = rebuildRouterNameGenerator(Kamon.config())

  Kamon.onReconfigure(newConfig => _routerNameGenerator = rebuildRouterNameGenerator(newConfig))

  private def rebuildRouterNameGenerator(config: Config): RouterOperationNameGenerator = {
    val nameGeneratorClazz = config.getString("kamon.instrumentation.play.http.server.extra.name-generator")
    Try(ClassLoading.createInstance[RouterOperationNameGenerator](nameGeneratorClazz)) match {
      case Failure(exception) =>
        _logger.error(s"Exception occurred on $nameGeneratorClazz instance creation, used default", exception)
        defaultRouterNameGenerator
      case Success(value) =>
        value
    }
  }

  @Advice.OnMethodEnter
  @static def enter(@Advice.Argument(0) request: RequestHeader): Unit = {
    request.attrs.get(Router.Attrs.HandlerDef).map(handler => {
      val span = Kamon.currentSpan()
      if (span.operationName() == "http.server.request") {
        span.name(_routerNameGenerator.generateOperationName(handler))
      }
      span.takeSamplingDecision()
    })
  }

}
