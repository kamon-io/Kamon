package kamon.instrumentation.akka.http;

import akka.NotUsed;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.scaladsl.Flow;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

public class HttpExtBindAndHandleAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(@Advice.Argument(value = 0, readOnly = false) Flow<HttpRequest, HttpResponse, NotUsed> handler,
     @Advice.Argument(1) String iface,
     @Advice.Argument(2) Integer port) {

    handler = ServerFlowWrapper.apply(handler, iface, port);
  }
}
