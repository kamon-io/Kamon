package kamon.instrumentation.akka.http;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import kamon.Kamon;
import kamon.context.Storage;
import kamon.instrumentation.http.HttpClientInstrumentation;
import kamon.instrumentation.http.HttpMessage;
import kamon.trace.Span;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import scala.concurrent.Future;

import static kamon.instrumentation.akka.http.AkkaHttpInstrumentation.toRequestBuilder;

public class PoolMasterDispatchRequestAdvice {

  @Advice.OnMethodEnter
  public static void onEnter(
      @Advice.Argument(value = 1, readOnly = false) HttpRequest request,
      @Advice.Local("handler") HttpClientInstrumentation.RequestHandler<HttpRequest> handler,
      @Advice.Local("scope")Storage.Scope scope) {

    final HttpMessage.RequestBuilder<HttpRequest> requestBuilder = toRequestBuilder(request);

    handler = AkkaHttpClientInstrumentation.httpClientInstrumentation()
        .createHandler(requestBuilder, Kamon.currentContext());

    request = handler.request();
    scope = Kamon.storeContext(Kamon.currentContext().withEntry(Span.Key(), handler.span()));
  }

  @Advice.OnMethodExit
  public static void onExit(
      @Advice.Return Future<HttpResponse> response,
      @Advice.Local("handler") HttpClientInstrumentation.RequestHandler<HttpRequest> handler,
      @Advice.Local("scope")Storage.Scope scope) {

    AkkaHttpClientInstrumentation.handleResponse(response, handler);
    scope.close();
  }
}
