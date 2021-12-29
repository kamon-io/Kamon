package kamon.instrumentation.finagle.client

import com.twitter.finagle.{Service, ServiceFactory, SimpleFilter, Stack}
import com.twitter.finagle.http.{Response, Status}
import com.twitter.util.{Return, Throw}

/**
 * Extract and report response HTTP status codes and exception conditions that may have been raised finishes processing
 * the active span.
 */
object ClientStatusTraceFilter {

  private def isError(status: Status): Boolean = status.code >= 400 && status.code < 600

  def filter[Req, Rep <: Response]: SimpleFilter[Req, Rep] = (request: Req, service: Service[Req, Rep]) => {
    service(request)
      .respond { response =>
        BroadcastRequestHandler.get.foreach { handler =>
          response match {
            case Return(r) =>
              Tags.setHttpResponseCategory(handler.span, r.status)
              if (isError(r.status))
                handler.span.fail(s"Error HTTP response code '${r.status.code}'")
              // processResponse will finish the span
              FinagleHttpInstrumentation.processResponse(r, handler)
            case Throw(e) => handler.span.fail(e)
          }
        }
      }
  }
}

final class ClientStatusTraceFilter[Req, Rep <: Response] extends Stack.Module0[ServiceFactory[Req, Rep]] {
  val role: Stack.Role = Stack.Role("StatusTraceFilter")
  val description: String = "Exposes responses' status to the finagle trace framework"

  override def make(next: ServiceFactory[Req, Rep]): ServiceFactory[Req, Rep] =
    ClientStatusTraceFilter.filter.andThen(next)
}
