/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

package kamon
package instrumentation
package http

import java.time.Duration

import com.typesafe.config.Config
import kamon.context.Context
import kamon.instrumentation.http.HttpServerInstrumentation.Settings
import kamon.instrumentation.trace.SpanTagger.TagMode
import kamon.instrumentation.tag.TagKeys
import kamon.instrumentation.trace.SpanTagger
import kamon.tag.Lookups.option
import kamon.trace.Span
import kamon.trace.Trace.SamplingDecision
import kamon.util.Filter
import org.slf4j.LoggerFactory

import scala.util.Try

/**
  * HTTP Server instrumentation handler that takes care of context propagation, distributed tracing and HTTP server
  * metrics. Instances can be created by using the `HttpServerInstrumentation.from` method with the desired
  * configuration. When any setting is missing on the provided configuration, it will be read from the default settings
  * found at "kamon.instrumentation.http-server.default".
  *
  * The default implementation shipping with Kamon provides:
  *
  *   * Context Propagation: Automatically transfers Context entries and tags using HTTP headers. Context propagation is
  *     further used to enable distributed tracing on top of any instrumented HTTP Server.
  *
  *   * Distributed Tracing: Automatically join traces initiated by the callers of this service and apply span and
  *     metric tags from the incoming requests as well as from the incoming context tags.
  *
  *   * Server Metrics: Basic request processing metrics to understand connection usage, throughput and response code
  *     counts in the HTTP server.
  *
  */
trait HttpServerInstrumentation {

  /**
    * Returns the interface on which the HTTP Server is listening.
    */
  def interface(): String

  /**
    * Returns the port on which the HTTP server is listening.
    */
  def port(): Int

  /**
    * Initiates handling of a HTTP request received by the server. The returned RequestHandler contains the Span that
    * represents the processing of the incoming HTTP request (if tracing is enabled) and the Context extracted from
    * HTTP headers (if context propagation is enabled).
    */
  def createHandler(request: HttpMessage.Request): HttpServerInstrumentation.RequestHandler =
    createHandler(request, deferSamplingDecision = false)

  /**
    * Initiates handling of a HTTP request received by the server. The returned RequestHandler contains the Span that
    * represents the processing of the incoming HTTP request (if tracing is enabled) and the Context extracted from
    * HTTP headers (if context propagation is enabled). It is possible to defer the sampling decision to a later
    * processing stage in case it was necessary, since in many HTTP servers the automatic instrumentation will try to
    * start Spans as early as possible to express times closer to what users will experience, but information like the
    * operation name that might be useful for taking a sampling decision is not available until reaching user code. When
    * the sampling decision is deferred, the returned Span will have an Unknown sampling decision and the
    * instrumentation or user code must call Span.takeSamplingDecision() on the Span.
    */
  def createHandler(
    request: HttpMessage.Request,
    deferSamplingDecision: Boolean
  ): HttpServerInstrumentation.RequestHandler

  /**
    * Signals that a new HTTP connection has been opened.
    */
  def connectionOpened(): Unit

  /**
    * Signals that a HTTP connection has been closed.
    *
    */
  def connectionClosed(): Unit =
    connectionClosed(Duration.ZERO, -1L)

  /**
    * Signals that a HTTP connection has been closed and reports for how long was that connection open.
    *
    */
  def connectionClosed(lifetime: Duration): Unit =
    connectionClosed(lifetime, -1L)

  /**
    * Signals that a HTTP connection has been closed and reports how many requests were handled by that connection.
    */
  def connectionClosed(handledRequests: Long): Unit =
    connectionClosed(Duration.ZERO, handledRequests)

  /**
    * Signals that a HTTP connection has been closed and reports for how long was that connection open and how many
    * requests were handled through it.
    */
  def connectionClosed(lifetime: Duration, handledRequests: Long): Unit

  /**
    * Frees resources that might have been acquired to provide the instrumentation. Behavior on HttpServer instances
    * after calling this function is undefined.
    */
  def shutdown(): Unit

  /**
    * Returns the Settings used to determine the behavior of the HTTP Server Instrumentation.
    */
  def settings: Settings

}

object HttpServerInstrumentation {

  private val _log = LoggerFactory.getLogger(classOf[Default])

  /**
    * Handler associated to the processing of a single request. The instrumentation code using this class is responsible
    * of creating a dedicated `HttpServer.RequestHandler` instance for each received request and invoking the
    * requestReceived, buildResponse and responseSent callbacks when appropriate.
    */
  trait RequestHandler {

    /**
      * If context propagation is enabled this function returns the incoming context associated wih this request,
      * otherwise `Context.Empty` is returned. When tracing is enabled, this Context will already contain the HTTP
      * Server Span representing the current request.
      */
    def context: Context

    /**
      * Span representing the current HTTP server operation. If tracing is disabled this will return an empty span.
      */
    def span: Span

    /**
      * Signals that the entire request (headers and body) has been received.
      */
    def requestReceived(): RequestHandler =
      requestReceived(-1L)

    /**
      * Signals that the entire request (headers and body) has been received and records the size of the received
      * payload.
      */
    def requestReceived(receivedBytes: Long): RequestHandler

    /**
      * Process a response to be sent back to the client. Since returning keys might need to included in the response
      * headers, users of this class must ensure that the returned HttpResponse is used instead of the original one
      * passed into this function.
      *
      * @param response Wraps the HTTP response to be sent back to the client.
      * @param context Context that should be used for writing returning keys into the response.
      * @return The modified HTTP response that should be sent to clients.
      */
    def buildResponse[HttpResponse](response: HttpMessage.ResponseBuilder[HttpResponse], context: Context): HttpResponse

    /**
      * Signals that the entire response (headers and body) has been sent to the client.
      */
    def responseSent(): Unit =
      responseSent(-1L)

    /**
      * Signals that the entire response (headers and body) has been sent to the client and records its size, if
      * available.
      */
    def responseSent(sentBytes: Long): Unit

  }

  /**
    * Creates a new HTTP Server Instrumentation, configured with the settings on the provided config path. If any of the
    * settings are missing they will be taken from the default HTTP server instrumentation. All HTTP server variants
    * must be configured under the "kamon.instrumentation.http-server" path, take a look at the "reference.conf" file
    * for more details.
    */
  def from(config: Config, component: String, interface: String, port: Int): HttpServerInstrumentation = {

    val defaultConfiguration = Kamon.config().getConfig(_defaultHttpServerConfiguration)
    val configWithFallback = config.withFallback(defaultConfiguration)

    new HttpServerInstrumentation.Default(Settings.from(configWithFallback), component, interface, port)
  }

  private val _defaultHttpServerConfiguration = "kamon.instrumentation.http-server.default"

  private class Default(val settings: Settings, component: String, val interface: String, val port: Int)
      extends HttpServerInstrumentation {

    private val _metrics =
      if (settings.enableServerMetrics) Some(HttpServerMetrics.of(component, interface, port)) else None
    private val _log = LoggerFactory.getLogger(classOf[Default])
    private val _propagation = Kamon.httpPropagation(settings.propagationChannel)
      .getOrElse {
        _log.warn(
          s"Could not find HTTP propagation [${settings.propagationChannel}], falling back to the default HTTP propagation"
        )
        Kamon.defaultHttpPropagation()
      }

    override def createHandler(request: HttpMessage.Request, deferSamplingDecision: Boolean): RequestHandler = {

      val incomingContext = if (settings.enableContextPropagation)
        _propagation.read(request)
      else Context.Empty

      val requestSpan = if (settings.enableTracing)
        buildServerSpan(incomingContext, request, deferSamplingDecision)
      else Span.Empty

      val handlerContext = if (!requestSpan.isEmpty)
        incomingContext.withEntry(Span.Key, requestSpan)
      else incomingContext

      _metrics.foreach { httpServerMetrics =>
        httpServerMetrics.activeRequests.increment()
      }

      new HttpServerInstrumentation.RequestHandler {
        override def context: Context =
          handlerContext

        override def span: Span =
          requestSpan

        override def requestReceived(receivedBytes: Long): RequestHandler = {
          if (receivedBytes >= 0) {
            _metrics.foreach { httpServerMetrics =>
              httpServerMetrics.requestSize.record(receivedBytes)
            }
          }

          this
        }

        override def buildResponse[HttpResponse](
          response: HttpMessage.ResponseBuilder[HttpResponse],
          context: Context
        ): HttpResponse = {
          _metrics.foreach { httpServerMetrics =>
            httpServerMetrics.countCompletedRequest(response.statusCode)
          }

          if (!span.isEmpty) {
            settings.traceIDResponseHeader.foreach(traceIDHeader => response.write(traceIDHeader, span.trace.id.string))
            settings.spanIDResponseHeader.foreach(spanIDHeader => response.write(spanIDHeader, span.id.string))
            settings.httpServerResponseHeaderGenerator.headers(handlerContext).foreach(header =>
              response.write(header._1, header._2)
            )

            SpanTagger.tag(span, TagKeys.HttpStatusCode, response.statusCode, settings.statusCodeTagMode)

            val statusCode = response.statusCode
            if (statusCode >= 500) {
              span.fail("Request failed with HTTP Status Code " + response.statusCode)
            }
          }

          response.build()
        }

        override def responseSent(sentBytes: Long): Unit = {
          _metrics.foreach { httpServerMetrics =>
            httpServerMetrics.activeRequests.decrement()

            if (sentBytes >= 0)
              httpServerMetrics.responseSize.record(sentBytes)
          }

          span.finish()
        }
      }
    }

    override def connectionOpened(): Unit = {
      _metrics.foreach { httpServerMetrics =>
        httpServerMetrics.openConnections.increment()
      }
    }

    override def connectionClosed(lifetime: Duration, handledRequests: Long): Unit = {
      _metrics.foreach { httpServerMetrics =>
        httpServerMetrics.openConnections.decrement()

        if (lifetime != Duration.ZERO)
          httpServerMetrics.connectionLifetime.record(lifetime.toNanos)

        if (handledRequests > 0)
          httpServerMetrics.connectionUsage.record(handledRequests)
      }
    }

    override def shutdown(): Unit = {
      _metrics.foreach { httpServerMetrics =>
        httpServerMetrics.remove()
      }
    }

    private def buildServerSpan(
      context: Context,
      request: HttpMessage.Request,
      deferSamplingDecision: Boolean
    ): Span = {
      val span = Kamon.serverSpanBuilder(settings.operationNameSettings.operationName(request), component)
      span.context(context)

      if (!settings.enableSpanMetrics)
        span.doNotTrackMetrics()

      if (deferSamplingDecision)
        span.samplingDecision(SamplingDecision.Unknown)

      for { traceIdTag <- settings.traceIDTag; customTraceID <- context.getTag(option(traceIdTag)) } {
        val identifier = Kamon.identifierScheme.traceIdFactory.from(customTraceID)
        if (!identifier.isEmpty)
          span.traceId(identifier)
      }

      SpanTagger.tag(span, TagKeys.HttpUrl, request.url, settings.urlTagMode)
      SpanTagger.tag(span, TagKeys.HttpMethod, request.method, settings.methodTagMode)
      settings.contextTags.foreach {
        case (tagName, mode) =>
          context
            .getTag(option(tagName))
            .foreach(tagValue => SpanTagger.tag(span, tagName, tagValue, mode))
      }

      span.start()
    }
  }

  final case class Settings(
    enableContextPropagation: Boolean,
    propagationChannel: String,
    enableServerMetrics: Boolean,
    enableTracing: Boolean,
    traceIDTag: Option[String],
    enableSpanMetrics: Boolean,
    urlTagMode: TagMode,
    methodTagMode: TagMode,
    statusCodeTagMode: TagMode,
    contextTags: Map[String, TagMode],
    traceIDResponseHeader: Option[String],
    spanIDResponseHeader: Option[String],
    defaultOperationName: String,
    unhandledOperationName: String,
    operationMappings: Map[Filter.Glob, String],
    operationNameGenerator: HttpOperationNameGenerator,
    httpServerResponseHeaderGenerator: HttpServerResponseHeaderGenerator
  ) {
    val operationNameSettings = OperationNameSettings(defaultOperationName, operationMappings, operationNameGenerator)
  }

  object Settings {

    def from(config: Config): Settings = {
      def optionalString(value: String): Option[String] = if (value.equalsIgnoreCase("none")) None else Some(value)

      // Context propagation settings
      val enablePropagation = config.getBoolean("propagation.enabled")
      val propagationChannel = config.getString("propagation.channel")

      // HTTP Server metrics settings
      val enableServerMetrics = config.getBoolean("metrics.enabled")

      // Tracing settings
      val enableTracing = config.getBoolean("tracing.enabled")
      val traceIdTag = Option(config.getString("tracing.preferred-trace-id-tag")).filterNot(_ == "none")
      val enableSpanMetrics = config.getBoolean("tracing.span-metrics")
      val urlTagMode = TagMode.from(config.getString("tracing.tags.url"))
      val methodTagMode = TagMode.from(config.getString("tracing.tags.method"))
      val statusCodeTagMode = TagMode.from(config.getString("tracing.tags.status-code"))
      val contextTags = config.getConfig("tracing.tags.from-context").pairs.map {
        case (tagName, mode) => (tagName, TagMode.from(mode))
      }

      val traceIDResponseHeader = optionalString(config.getString("tracing.response-headers.trace-id"))
      val spanIDResponseHeader = optionalString(config.getString("tracing.response-headers.span-id"))

      val httpServerResponseHeaderGenerator: Try[HttpServerResponseHeaderGenerator] = Try {
        config.getString("tracing.response-headers.headers-generator") match {
          case "none" => DefaultHttpServerResponseHeaderGenerator
          case fqcn   => ClassLoading.createInstance[HttpServerResponseHeaderGenerator](fqcn)
        }
      } recover {
        case t: Throwable =>
          _log.warn("Failed to create an HTTP Server Response Header Generator, falling back to the default no-op", t)
          DefaultHttpServerResponseHeaderGenerator
      }

      val defaultOperationName = config.getString("tracing.operations.default")
      val operationNameGenerator: Try[HttpOperationNameGenerator] = Try {
        config.getString("tracing.operations.name-generator") match {
          case "default" => new HttpOperationNameGenerator.Static(defaultOperationName)
          case "method"  => HttpOperationNameGenerator.Method
          case fqcn      => ClassLoading.createInstance[HttpOperationNameGenerator](fqcn)
        }
      } recover {
        case t: Throwable =>
          _log.warn("Failed to create an HTTP Operation Name Generator, falling back to the default operation name", t)
          new HttpOperationNameGenerator.Static(defaultOperationName)
      }
      val unhandledOperationName = config.getString("tracing.operations.unhandled")
      val operationMappings = config.getConfig("tracing.operations.mappings").pairs.map {
        case (pattern, operationName) => (Filter.Glob(pattern), operationName)
      }

      Settings(
        enablePropagation,
        propagationChannel,
        enableServerMetrics,
        enableTracing,
        traceIdTag,
        enableSpanMetrics,
        urlTagMode,
        methodTagMode,
        statusCodeTagMode,
        contextTags,
        traceIDResponseHeader,
        spanIDResponseHeader,
        defaultOperationName,
        unhandledOperationName,
        operationMappings,
        operationNameGenerator.get,
        httpServerResponseHeaderGenerator.get
      )
    }
  }
}
