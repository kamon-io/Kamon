package kamon.instrumentation.spring.client;

import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

public class ExchangeAdvice {
    @Advice.OnMethodEnter()
    public static RequestHandler<ClientRequest> enter(@Advice.Argument(value = 0, readOnly = false) ClientRequest request) {
        RequestHandler<ClientRequest> handler = ClientInstrumentation.getHandler(request);
        request = handler.request();
        return handler;

    }

    @Advice.OnMethodExit
    public static void exit(@Advice.Enter RequestHandler<ClientRequest> handler,
                            @Advice.Return(readOnly = false) Mono<ClientResponse> responseWrapper) {
        responseWrapper = ClientInstrumentation.wrapResponse(responseWrapper, handler);
    }
}
