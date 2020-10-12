package kamon.instrumentation.elasticsearch;

import kamon.Kamon;
import kamon.trace.Span;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseListener;

public class AsyncElasticsearchRestClientInstrumentation {

    @Advice.OnMethodEnter
    public static void enter(
            @Advice.Argument(0) Request request,
            @Advice.Argument(value = 1, readOnly = false) ResponseListener responseListener) {
        final String operationName =
                RequestNameConverter.convert(
                        HighLevelElasticsearchClientInstrumentation.requestClassName.get(),
                        "AsyncRequest");

        Span span = Kamon.clientSpanBuilder(operationName, "elasticsearch.client")
                .tag("elasticsearch.http.endpoint", request.getEndpoint())
                .tag("elasticsearch.http.method", request.getMethod())
                .start();

        RequestSizeHistogram.record(request.getEntity());
        responseListener = new InstrumentedListener(responseListener, span);
    }
}
