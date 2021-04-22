/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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
      case endpoint @ ServerEndpoint(_, _) => {
        val originalRoute = superCall.call()

        req => {
          val useEndpointNameAsOperationName = Kamon.config()
            .getBoolean("kamon.instrumentation.tapir.use-endpoint-name-as-operation-name")

          val operationName = endpoint.info.name match {
            case Some(endpointName) if useEndpointNameAsOperationName => endpointName
            case _ => endpoint.renderPathTemplate()
          }

          Kamon.currentSpan()
            .name(operationName)
            .takeSamplingDecision()

          originalRoute(req)
        }
      }
      case _ =>
        // Ignores the version of .toRoute that takes List[ServerEndpoint[...]]
        superCall.call()
    }
  }
}
