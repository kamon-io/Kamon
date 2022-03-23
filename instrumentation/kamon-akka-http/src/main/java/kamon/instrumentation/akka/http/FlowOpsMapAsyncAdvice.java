package kamon.instrumentation.akka.http;

import akka.NotUsed;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.scaladsl.Flow;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

public class FlowOpsMapAsyncAdvice {

  public static class EndpointInfo {
    public final String listenInterface;
    public final int listenPort;

    public EndpointInfo(String listenInterface, int listenPort) {
      this.listenInterface = listenInterface;
      this.listenPort = listenPort;
    }
  }

  public static ThreadLocal<EndpointInfo> currentEndpoint = new ThreadLocal<>();

  @Advice.OnMethodExit
  public static void onExit(@Advice.Return(readOnly = false) akka.stream.scaladsl.FlowOps returnedFlow) {
    EndpointInfo bindAndHandlerEndpoint = currentEndpoint.get();

    if(bindAndHandlerEndpoint != null) {
      returnedFlow = ServerFlowWrapper.apply(
          (Flow<HttpRequest, HttpResponse, NotUsed>) returnedFlow,
          bindAndHandlerEndpoint.listenInterface,
          bindAndHandlerEndpoint.listenPort
      );
    }
  }
}
