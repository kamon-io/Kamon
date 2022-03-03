package kamon.instrumentation.akka.grpc

import kamon.Kamon
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

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
  onType("akka.grpc.internal.NoOpTelemetry$")
    .advise(method("onRequest"), AkkaGRPCServerRequestHandler)
}

object AkkaGRPCServerRequestHandler {

  @Advice.OnMethodEnter()
  def enter(@Advice.Argument(0) serviceName: String, @Advice.Argument(1) method: String): Unit = {
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
