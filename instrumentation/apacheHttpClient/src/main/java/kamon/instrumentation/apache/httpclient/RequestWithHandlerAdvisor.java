package kamon.instrumentation.apache.httpclient;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.ResponseHandler;

import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage.Scope;
import kamon.instrumentation.context.HasContext;
import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler;
import kamon.instrumentation.http.HttpMessage.RequestBuilder;
import kamon.trace.Span;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

public class RequestWithHandlerAdvisor {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) HttpHost host,
            @Advice.Argument(value = 1, readOnly = false) HttpRequest request,
            @Advice.Argument(value = 2, readOnly = false) ResponseHandler<?> resHandler,
            @Advice.Local("handler") RequestHandler<HttpRequest> reqHandler,
            @Advice.Local("scope") Scope scope) {
        if (((HasContext) request).context().nonEmpty()) {
            // Request has been instrumented already
            return;
        }
        final Context parentContext = Kamon.currentContext();
        final RequestBuilder<HttpRequest> builder = ApacheHttpClientHelper.toRequestBuilder(host, request);
        reqHandler = ApacheHttpClientInstrumentation.httpClientInstrumentation().createHandler(builder, parentContext);
        resHandler = new ResponseHandlerProxy<>(reqHandler, resHandler, parentContext);
        final Context ctx = parentContext.withEntry(Span.Key(), reqHandler.span());
        scope = Kamon.storeContext(ctx);
        request = reqHandler.request();
        ((HasContext) request).setContext(ctx);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Local("scope") Scope scope) {
        if (scope != null) {
            scope.close();
        }
    }
}