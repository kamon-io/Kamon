package kamon.instrumentation.tapir

import akka.http.scaladsl.server.Route
import kamon.Kamon
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{Argument, SuperCall}
import sttp.tapir.server.ServerEndpoint

import java.util.concurrent.Callable

class TapirInstrumentation extends InstrumentationBuilder {
  onTypes("sttp.tapir.server.akkahttp.EndpointToAkkaServer",
    "sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter")
    .intercept(method("toRoute"), classOf[TapirToRouteInterceptor])
}

class TapirToRouteInterceptor

object TapirToRouteInterceptor {
  def toRoute[I, E, O](@Argument(0) arg: Any, @SuperCall superCall: Callable[Route]): Route = {
    arg match {
      case endpoint@ServerEndpoint(_, _) => {
        val originalRoute = superCall.call()
        req => {
          val endpointName = Kamon.config().getBoolean("kamon.instrumentation.tapir.endpoint-name")
          if (endpointName && endpoint.info.name.isDefined) {
            endpoint.info.name
              .foreach(Kamon.currentSpan().name(_).takeSamplingDecision())
          } else {
            Kamon.currentSpan()
              .name(endpoint.renderPathTemplate())
              .takeSamplingDecision()
          }
          originalRoute(req)
        }
      }
      case _ =>
        // Ignores the version of .toRoute that takes List[ServerEndpoint[...]]
        superCall.call()
    }
  }
}
