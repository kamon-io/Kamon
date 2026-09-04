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

import com.typesafe.config.Config
import kamon.context.Context
import kamon.instrumentation.http.HttpClientInstrumentation.Settings
import kamon.instrumentation.tag.TagKeys
import kamon.instrumentation.trace.SpanTagger
import kamon.instrumentation.trace.SpanTagger.TagMode
import kamon.tag.Lookups.option
import kamon.trace.Span
import kamon.util.Filter
import org.slf4j.LoggerFactory

import scala.util.Try

/**
  * HTTP Client instrumentation handler that takes care of context propagation and distributed tracing. Instances can be
  * created by using the `HttpClientInstrumentation.from` method with the desired configuration. When any setting is
  * missing on the provided configuration, it will be read from the default settings found at
  * "kamon.instrumentation.http-client.default".
  *
  * The default implementation shipping with Kamon provides:
  *
  *   * Context Propagation: Automatically transfers Context entries and tags using HTTP headers. Context propagation is
  *     further used to enable distributed tracing on top of any instrumented HTTP Client.
  *
  *   * Distributed Tracing: Automatically creates Spans for HTTP Client calls, ensuring that the required information
  *     to continue the distributed trace is sent along with the request headers.
  */
trait HttpClientInstrumentation {

  /**
    * Returns a new request handler for a HTTP client request. Users of this class must ensure that the HTTP request
    * instance contained in the handler replaces the original HTTP request sent by the user, since the updated HTTP
    * request will have all the required additional headers to enable context propagation and distributed tracing.
    */
  def createHandler[T](
    request: HttpMessage.RequestBuilder[T],
    context: Context
  ): HttpClientInstrumentation.RequestHandler[T]

  /**
    * Returns the settings currently controlling the HTTP client instrumentation. The effective settings will be taken
    * from the Configuration object provided by the user when creating the HTTP client instrumentation and the default
    * values found under "kamon.instrumentation.http-client.default"
    */
  def settings: Settings
}

object HttpClientInstrumentation {

  private val _log = LoggerFactory.getLogger(classOf[Default])

  /**
    * Handle associated to the processing of a single HTTP client request. The instrumentation code is responsible of
    * create a dedicated instance for each handled request and ensure that the processResponse callback is invoked when
    * the HTTP response is received or otherwise failed.
    */
  trait RequestHandler[T] {

    /**
      * Returns the actual HTTP request that should be sent to the external services. Depending on the configuration
      * settings, this request might have additional HTTP headers that enable context propagation and distributed
      * tracing.
      */
    def request: T

    /**
      * Returns the Span created to represent the HTTP request, or the Empty Span if distributed tracing is disabled.
      */
    def span: Span

    /**
      * Signals that a response has been received from the external service and processing of the request has finished.
      */
    def processResponse(response: HttpMessage.Response): Unit

  }

  /**
    * Creates a new HTTP Server Instrumentation, configured with the settings on the provided config path. If any of the
    * settings are missing they will be taken from the default HTTP server instrumentation. All HTTP server variants
    * must be configured under the "kamon.instrumentation.http-server" path, take a look at the "reference.conf" file
    * for more details.
    */
  def from(config: Config, component: String): HttpClientInstrumentation = {
    val defaultConfiguration = Kamon.config().getConfig(_defaultHttpClientConfiguration)
    val configWithFallback = config.withFallback(defaultConfiguration)

    new HttpClientInstrumentation.Default(Settings.from(configWithFallback), component)
  }

  private val _defaultHttpClientConfiguration = "kamon.instrumentation.http-client.default"

  private class Default(val settings: Settings, component: String) extends HttpClientInstrumentation {
    private val _propagation = Kamon.httpPropagation(settings.propagationChannel)
      .getOrElse {
        _log.warn(
          s"Could not find HTTP propagation [${settings.propagationChannel}], falling back to the default HTTP propagation"
        )
        Kamon.defaultHttpPropagation()
      }

    override def createHandler[T](
      requestBuilder: HttpMessage.RequestBuilder[T],
      context: Context
    ): RequestHandler[T] = {
      val shouldCreateSpan = !Kamon.currentSpan().isEmpty || settings.startTrace
      val requestSpan: Span = {
        if (settings.enableContextPropagation && shouldCreateSpan) {
          val contextToPropagate = if (settings.enableTracing) {
            context.withEntry(Span.Key, createClientSpan(requestBuilder, context))
          } else {
            context.withoutEntry(Span.Key)
          }

          _propagation.write(contextToPropagate, requestBuilder)
          contextToPropagate.get(Span.Key)

        } else Span.Empty
      }

      // At this point, if anything needed to be written on HTTP headers it would have been written already.
      val builtRequest = requestBuilder.build()

      new RequestHandler[T] {
        override val span: Span = requestSpan
        override val request: T = builtRequest

        override def processResponse(response: HttpMessage.Response): Unit = {
          val statusCode = response.statusCode
          if (statusCode >= 500) {
            span.fail("Request failed with HTTP Status Code " + statusCode)
          }

          SpanTagger.tag(span, TagKeys.HttpStatusCode, statusCode, settings.statusCodeTagMode)
          span.finish()
        }
      }
    }

    private def createClientSpan(requestMessage: HttpMessage.Request, context: Context): Span = {
      val span = Kamon
        .clientSpanBuilder(settings.operationNameSettings.operationName(requestMessage), component)
        .context(context)

      if (!settings.enableSpanMetrics)
        span.doNotTrackMetrics()

      SpanTagger.tag(span, TagKeys.HttpUrl, requestMessage.url, settings.urlTagMode)
      SpanTagger.tag(span, TagKeys.HttpMethod, requestMessage.method, settings.methodTagMode)

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
    enableTracing: Boolean,
    enableSpanMetrics: Boolean,
    urlTagMode: TagMode,
    methodTagMode: TagMode,
    statusCodeTagMode: TagMode,
    contextTags: Map[String, TagMode],
    defaultOperationName: String,
    operationMappings: Map[Filter.Glob, String],
    operationNameGenerator: HttpOperationNameGenerator,
    startTrace: Boolean
  ) {
    val operationNameSettings = OperationNameSettings(defaultOperationName, operationMappings, operationNameGenerator)
  }

  object Settings {

    def from(config: Config): Settings = {

      // Context propagation settings
      val enablePropagation = config.getBoolean("propagation.enabled")
      val propagationChannel = config.getString("propagation.channel")

      // Tracing settings
      val enableTracing = config.getBoolean("tracing.enabled")
      val enableSpanMetrics = config.getBoolean("tracing.span-metrics")
      val urlTagMode = TagMode.from(config.getString("tracing.tags.url"))
      val methodTagMode = TagMode.from(config.getString("tracing.tags.method"))
      val statusCodeTagMode = TagMode.from(config.getString("tracing.tags.status-code"))
      val contextTags = config.getConfig("tracing.tags.from-context").pairs.map {
        case (tagName, mode) => (tagName, TagMode.from(mode))
      }

      val defaultOperationName = config.getString("tracing.operations.default")
      val operationNameGenerator: Try[HttpOperationNameGenerator] = Try {
        config.getString("tracing.operations.name-generator") match {
          case "hostname" => HttpOperationNameGenerator.Hostname
          case "method"   => HttpOperationNameGenerator.Method
          case fqcn       => ClassLoading.createInstance[HttpOperationNameGenerator](fqcn)
        }
      } recover {
        case t: Throwable =>
          _log.warn("Failed to create an HTTP Operation Name Generator, falling back to the default operation name", t)
          new HttpOperationNameGenerator.Static(defaultOperationName)
      }
      val operationMappings = config.getConfig("tracing.operations.mappings").pairs.map {
        case (pattern, operationName) => (Filter.Glob(pattern), operationName)
      }

      val startTrace = config.getBoolean("tracing.start-trace")

      Settings(
        enablePropagation,
        propagationChannel,
        enableTracing,
        enableSpanMetrics,
        urlTagMode,
        methodTagMode,
        statusCodeTagMode,
        contextTags,
        defaultOperationName,
        operationMappings,
        operationNameGenerator.get,
        startTrace
      )
    }
  }
}
