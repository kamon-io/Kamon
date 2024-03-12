package kamon.instrumentation.apache.httpclient;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler;
import kamon.instrumentation.http.HttpMessage.RequestBuilder;
import kamon.trace.Span;
import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage.Scope;
import kamon.instrumentation.context.HasContext;

import kanela.agent.libs.net.bytebuddy.asm.Advice;

public class RequestAdvisor {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) HttpHost host,
            @Advice.Argument(value = 1, readOnly = false) HttpRequest request,
            @Advice.Local("handler") RequestHandler<HttpRequest> handler,
            @Advice.Local("scope") Scope scope) {
        if (((HasContext) request).context().nonEmpty()) {
            // Request has been instrumented already
            return;
        }
        final Context parentContext = Kamon.currentContext();
        final RequestBuilder<HttpRequest> builder = ApacheHttpClientHelper.toRequestBuilder(host, request);
        handler = ApacheHttpClientInstrumentation.httpClientInstrumentation().createHandler(builder, parentContext);
        final Context ctx = parentContext.withEntry(Span.Key(), handler.span());
        scope = Kamon.storeContext(ctx);
        request = handler.request();
        ((HasContext) request).setContext(ctx);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Return HttpResponse response,
            @Advice.Thrown Throwable t,
            @Advice.Local("handler") RequestHandler<HttpRequest> handler,
            @Advice.Local("scope") Scope scope) {
        if (scope == null) {
            return;
        }
        ApacheHttpClientInstrumentation.processResponse(handler, response, t);
        scope.close();
    }
}