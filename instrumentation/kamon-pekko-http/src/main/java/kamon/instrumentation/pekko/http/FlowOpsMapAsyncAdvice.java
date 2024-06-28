package kamon.instrumentation.pekko.http;

import org.apache.pekko.NotUsed;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import org.apache.pekko.stream.scaladsl.Flow;
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
  public static void onExit(@Advice.Return(readOnly = false) org.apache.pekko.stream.scaladsl.FlowOps returnedFlow) {
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
