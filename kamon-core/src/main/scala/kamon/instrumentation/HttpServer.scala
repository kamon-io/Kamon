package kamon
package instrumentation

import com.typesafe.config.Config
import kamon.context.Context
import kamon.context.HttpPropagation.Direction
import kamon.instrumentation.HttpServer.Settings.TagMode
import kamon.trace.Span
import kamon.util.GlobPathFilter

import scala.collection.JavaConverters._

trait HttpServer {

  def handle(request: HttpRequest): HttpServer.Handler

}

object HttpServer {

  trait Handler {

    def context: Context

    def span: Span

    def finishRequest(): Unit

    def startResponse[HttpResponse](response: HttpResponse.Writable[HttpResponse], context: Context): HttpResponse

    def endResponse(): Unit
  }

  def from(name: String, port: Int, component: String): HttpServer = {
    from(name, port, component, Kamon, Kamon)
  }

  def from(name: String, port: Int, component: String, configuration: Configuration, contextPropagation: ContextPropagation): HttpServer = {
    val configKey = "kamon.instrumentation.http-server." + name
    new HttpServer.Default(Settings.from(configuration.config().getConfig(configKey)), contextPropagation, port, component)
  }


  class Default(settings: Settings, contextPropagation: ContextPropagation, port: Int, component: String) extends HttpServer {
    private val _propagation = contextPropagation.httpPropagation(settings.propagationChannel)
      .getOrElse(sys.error(s"Could not find HTTP propagation [${settings.propagationChannel}"))

    override def handle(request: HttpRequest): Handler = {
      val incomingContext = if(settings.enableContextPropagation)
        _propagation.readContext(request)
        else Context.Empty

      val requestSpan = if(settings.enableTracing)
        buildServerSpan(incomingContext, request)
        else Span.Empty

      val handlerContext = if(requestSpan.nonEmpty())
        incomingContext.withKey(Span.ContextKey, requestSpan)
      else incomingContext

      // TODO: Handle HTTP Server Metrics


      new HttpServer.Handler {
        override def context: Context =
          handlerContext

        override def span: Span =
          requestSpan

        override def finishRequest(): Unit = {}

        override def startResponse[HttpResponse](response: HttpResponse.Writable[HttpResponse], context: Context): HttpResponse = {
          if(settings.enableContextPropagation) {
            _propagation.writeContext(context, response, Direction.Returning)
          }

          response.build()
        }

        override def endResponse(): Unit = {
          span.finish()
        }
      }
    }

    private def buildServerSpan(context: Context, request: HttpRequest): Span = {
      val span = Kamon.buildSpan(operationName(request))
        .withMetricTag("span.kind", "server")
        .withMetricTag("component", component)

      def addTag(tag: String, value: String, mode: TagMode): Unit = mode match {
        case TagMode.Metric => span.withMetricTag(tag, value)
        case TagMode.Span => span.withTag(tag, value)
        case TagMode.Off =>
      }

      addTag("http.url", request.url, settings.urlTagMode)
      addTag("http.method", request.method, settings.urlTagMode)
      settings.contextTags.foreach {
        case (tagName, mode) => context.getTag(tagName).foreach(tagValue => addTag(tagName, tagValue, mode))
      }

      span.start()
    }

    private def operationName(request: HttpRequest): String = {
      val requestPath = request.path
      val customMapping = settings.operationMappings.find {
        case (pattern, _) => pattern.accept(requestPath)
      }.map(_._2)

      customMapping.getOrElse("http.request")
    }
  }


  case class Settings(
    enableContextPropagation: Boolean,
    propagationChannel: String,
    enableServerMetrics: Boolean,
    serverMetricsTags: Seq[String],
    enableTracing: Boolean,
    traceIDTag: Option[String],
    enableSpanMetrics: Boolean,
    urlTagMode: TagMode,
    methodTagMode: TagMode,
    statusCodeTagMode: TagMode,
    contextTags: Map[String, TagMode],
    unhandledOperationName: String,
    operationMappings: Map[GlobPathFilter, String]
  )

  object Settings {

    sealed trait TagMode
    object TagMode {
      case object Metric extends TagMode
      case object Span extends TagMode
      case object Off extends TagMode

      def from(value: String): TagMode = value.toLowerCase match {
        case "metric" => TagMode.Metric
        case "span" => TagMode.Span
        case _ => TagMode.Off
      }
    }

    def from(config: Config): Settings = {

      // Context propagation settings
      val enablePropagation = config.getBoolean("propagation.enabled")
      val propagationChannel = config.getString("propagation.channel")

      // HTTP Server metrics settings
      val enableServerMetrics = config.getBoolean("metrics.enabled")
      val serverMetricsTags = config.getStringList("metrics.tags").asScala

      // Tracing settings
      val enableTracing = config.getBoolean("tracing.enabled")
      val traceIdTag = Option(config.getString("tracing.trace-id-tag")).filterNot(_ == "none")
      val enableSpanMetrics = config.getBoolean("tracing.span-metrics")
      val urlTagMode = TagMode.from(config.getString("tracing.tags.url"))
      val methodTagMode = TagMode.from(config.getString("tracing.tags.method"))
      val statusCodeTagMode = TagMode.from(config.getString("tracing.tags.status-code"))
      val contextTags = config.getConfig("tracing.tags.from-context").pairs.map {
        case (tagName, mode) => (tagName, TagMode.from(mode))
      }

      val unhandledOperationName = config.getString("tracing.operations.unhandled")
      val operationMappings = config.getConfig("tracing.operations.mappings").pairs.map {
        case (pattern, operationName) => (new GlobPathFilter(pattern), operationName)
      }

      Settings(
        enablePropagation,
        propagationChannel,
        enableServerMetrics,
        serverMetricsTags,
        enableTracing,
        traceIdTag,
        enableSpanMetrics,
        urlTagMode,
        methodTagMode,
        statusCodeTagMode,
        contextTags,
        unhandledOperationName,
        operationMappings
      )
    }
  }
}
