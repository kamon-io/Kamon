package kamon.http4k

import kamon.Kamon
import kamon.instrumentation.http.HttpServerInstrumentation
import kotlin.jvm.functions.Function1
import org.http4k.core.{Filter, Request, Response}

/** Kamon server instrumentation for http4k.
  *
  * Wraps any [[HttpHandler]] with Kamon HTTP server instrumentation.
  *
  * http4k's instrumentation point is the [[Filter]] abstraction,
  * which is independent of the underlying server backend.
  * This then works with every http4k server backend.
  *
  * Usage:
  * {{{
  *   val app: HttpHandler = routes(...)
  *   val instrumented: HttpHandler = KamonFilter("0.0.0.0", 8080).then(app)
  *   instrumented.asServer(Netty(8080)).start()
  * }}}
  */
object KamonFilter {

  // For: typealias HttpHandler = (request: Request) -> Response
  type WildcardHandler = Function1[_ >: Request, _ <: Response]
  type HttpHandler = Function1[Request, Response]

  /** Build a [[Filter]] that applies Kamon HTTP server instrumentation to every request.
    */
  def apply(interface: String, port: Int): Filter = {
    val config = Kamon.config().getConfig("kamon.instrumentation.http4k.server")
    val instrumentation = HttpServerInstrumentation.from(config, "http4k.server", interface, port)

    // fun interface Filter : (HttpHandler) -> HttpHandler
    new Filter {
      override def invoke(next: WildcardHandler): WildcardHandler =
        new HttpHandler {
          override def invoke(request: Request): Response = {
            val handler = instrumentation.createHandler(buildRequestMessage(request))
            val scope = Kamon.storeContext(handler.context)

            try {
              handler.requestReceived()
              val response = next.invoke(request)
              if (response.getStatus().getCode() == 404) {
                handler.span.name(instrumentation.settings.unhandledOperationName)
              }
              val finalResponse = handler.buildResponse(buildResponseBuilder(response), handler.context)
              handler.responseSent()
              finalResponse
            } catch {
              case e: Throwable =>
                handler.span.fail(e.getMessage)
                handler.buildResponse(errorResponseBuilder, handler.context)
                handler.responseSent()
                throw e
            } finally {
              scope.close()
            }
          }
        }
    }
  }
}
