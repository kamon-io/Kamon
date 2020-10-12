package kamon.instrumentation.elasticsearch;

import kamon.Kamon;
import kamon.trace.Span;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import org.elasticsearch.client.Request;

public class SyncElasticsearchRestClientInstrumentation {

    @Advice.OnMethodEnter
    public static void enter(
            @Advice.Argument(0) Request request,
            @Advice.Local("span") Span span) {
        final String operationName = RequestNameConverter.convert(
                HighLevelElasticsearchClientInstrumentation.requestClassName.get(),
                "SyncRequest");

        RequestSizeHistogram.record(request.getEntity());
        span = Kamon.clientSpanBuilder(operationName, "elasticsearch.client")
                .tag("elasticsearch.http.endpoint", request.getEndpoint())
                .tag("elasticsearch.http.method", request.getMethod())
                .start();
    }

    @Advice.OnMethodExit
    public static void exit(
            @Advice.Local("span") Span span) {
        span.finish();
    }
}
