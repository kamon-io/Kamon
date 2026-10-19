/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
