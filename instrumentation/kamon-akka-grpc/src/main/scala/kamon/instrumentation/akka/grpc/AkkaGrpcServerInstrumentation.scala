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

package kamon.instrumentation.akka.grpc

import kamon.Kamon
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.annotation.static

class AkkaGrpcServerInstrumentation extends InstrumentationBuilder {

  /**
    * Support for Akka gRPC servers.
    *
    * gRPC requests get their spans started by the ServerFlowWrapper in the Akka HTTP instrumentation like any other
    * requests, but they never go through any instrumentation that gives a good operation name to the Span and forces
    * taking a sampling decision.
    *
    * This instrumentation gives a proper name and tags to the span when it matches one of the exposed services,
    * otherwise the span remains unchanged. Assumes no actual implementation of `akka.grpc.internal.TelemetrySpi` is
    * configured.
    */
  onType("akka.grpc.internal.TelemetrySpi")
    .advise(method("onRequest"), classOf[AkkaGRPCServerRequestHandler])


  onType("akka.grpc.javadsl.GrpcMarshalling")
    .advise(method("unmarshal"), classOf[AkkaGRPCUnmarshallingContextPropagation])
}

class AkkaGRPCServerRequestHandler
object AkkaGRPCServerRequestHandler {

  @Advice.OnMethodEnter()
  @static def enter(@Advice.Argument(0) serviceName: String, @Advice.Argument(1) method: String): Unit = {
    val fullSpanName = serviceName + "/" + method
    Kamon.currentSpan()
      .name(fullSpanName)
      .tagMetrics("component", "akka.grpc.server")
      .tagMetrics("rpc.system", "grpc")
      .tagMetrics("rpc.service", serviceName)
      .tagMetrics("rpc.method", method)
      .takeSamplingDecision()
  }
}
