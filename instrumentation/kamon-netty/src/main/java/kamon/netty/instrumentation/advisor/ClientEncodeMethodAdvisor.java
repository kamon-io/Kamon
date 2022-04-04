package kamon.netty.instrumentation.advisor;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import kamon.netty.instrumentation.HttpRequestContext;
import kanela.agent.libs.net.bytebuddy.asm.Advice.Argument;
import kanela.agent.libs.net.bytebuddy.asm.Advice.OnMethodEnter;

public class ClientEncodeMethodAdvisor {

  @OnMethodEnter
  static void onEnter(@Argument(value = 0) ChannelHandlerContext ctx,
                      @Argument(value = 1, readOnly = false) Object request) {
      if (request instanceof HttpRequest) {
          request = HttpRequestContext.withContext((HttpRequest)request, ctx);
      }
  }
}
