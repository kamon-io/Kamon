package kamon.instrumentation.akka.grpc;

import akka.grpc.internal.MetadataImpl;
import kamon.Kamon;
import kamon.context.Storage;
import kamon.trace.Span;
import kamon.trace.SpanBuilder;
import kamon.util.CallingThreadExecutionContext;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import scala.concurrent.Future;

public class AkkaGrpcClientInstrumentationAdvise {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static <I> void enter(@Advice.Argument(0) I request, @Advice.Local("scope") Storage.Scope scope) {
        String requestStr = request.toString();
        String serviceName = requestStr.substring(0, requestStr.indexOf("("));
        SpanBuilder spanBuilder = Kamon.spanBuilder("grpc " + serviceName)
                .tagMetrics("component", "akka.grpc.client")
                .tagMetrics("rpc.system", "grpc")
                .tagMetrics("rpc.service", serviceName);

        if (!Kamon.currentSpan().isEmpty()) {
            spanBuilder.asChildOf(Kamon.currentSpan());
        }
        Span span = spanBuilder.start().takeSamplingDecision();
        scope = Kamon.storeContext(Kamon.currentContext().withEntry(Span.Key(), span));
    }

    @Advice.OnMethodExit
    public static <I, R> Future<R> onExit(
            @Advice.Argument(0) I request,
            @Advice.Local("scope") Storage.Scope scope,
            @Advice.Return Future<R> responseFuture) {
        return AkkaGrpcClientInstrumentation.onExit(request, scope, responseFuture);
    }
}
