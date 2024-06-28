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

package kamon.instrumentation.pekko.http;

import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import kamon.Kamon;
import kamon.context.Storage;
import kamon.instrumentation.http.HttpClientInstrumentation;
import kamon.instrumentation.http.HttpMessage;
import kamon.trace.Span;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import scala.concurrent.Future;
import static kamon.instrumentation.pekko.http.PekkoHttpInstrumentation.toRequestBuilder;

public class HttpExtSingleRequestAdvice {

  @Advice.OnMethodEnter
  public static void onEnter(@Advice.Argument(value = 0, readOnly = false) HttpRequest request,
                             @Advice.Local("handler") HttpClientInstrumentation.RequestHandler<HttpRequest> handler,
                             @Advice.Local("scope")Storage.Scope scope) {

    final HttpMessage.RequestBuilder<HttpRequest> requestBuilder = toRequestBuilder(request);

    handler = PekkoHttpClientInstrumentation.httpClientInstrumentation()
        .createHandler(requestBuilder, Kamon.currentContext());

    request = handler.request();
    scope = Kamon.storeContext(Kamon.currentContext().withEntry(Span.Key(), handler.span()));
  }

  @Advice.OnMethodExit
  public static void onExit(@Advice.Return Future<HttpResponse> response,
                            @Advice.Local("handler") HttpClientInstrumentation.RequestHandler<HttpRequest> handler,
                            @Advice.Local("scope")Storage.Scope scope) {

    PekkoHttpClientInstrumentation.handleResponse(response, handler);
    scope.close();
  }
}
