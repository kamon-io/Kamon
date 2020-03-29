package kamon.instrumentation.akka.http;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import scala.Function1;
import scala.concurrent.Future;

public class Http2ExtBindAndHandleAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(@Advice.Argument(value = 0, readOnly = false) Function1<HttpRequest, Future<HttpResponse>> handler,
     @Advice.Argument(1) String iface,
     @Advice.Argument(2) Integer port) {

    handler = new Http2BlueprintInterceptor.HandlerWithEndpoint(iface, port, handler);
  }
}
