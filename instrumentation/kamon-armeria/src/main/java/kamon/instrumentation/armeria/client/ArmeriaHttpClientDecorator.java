/* =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.instrumentation.armeria.client;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.SimpleDecoratingHttpClient;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import kamon.Kamon;
import kamon.instrumentation.armeria.client.timing.Timing;
import kamon.instrumentation.armeria.converters.KamonArmeriaMessageConverter;
import kamon.instrumentation.http.HttpClientInstrumentation;

public class ArmeriaHttpClientDecorator extends SimpleDecoratingHttpClient {
  private final HttpClientInstrumentation clientInstrumentation;

  protected ArmeriaHttpClientDecorator(HttpClient delegate, HttpClientInstrumentation clientInstrumentation) {
    super(delegate);
    this.clientInstrumentation = clientInstrumentation;
  }

  @Override
  public HttpResponse execute(ClientRequestContext ctx, HttpRequest req) throws Exception {

    final HttpClientInstrumentation.RequestHandler<HttpRequest> requestHandler =
        clientInstrumentation.createHandler(KamonArmeriaMessageConverter.getRequestBuilder(req), Kamon.currentContext());

    ctx.log()
        .whenComplete()
        .thenAccept(log -> {
            Timing.takeTimings(log, requestHandler.span());
            requestHandler.processResponse(KamonArmeriaMessageConverter.toKamonResponse(log));
        });

    try {
      return unwrap().execute(ctx, requestHandler.request());
    } catch (Exception exception) {
      requestHandler.span().fail(exception.getMessage(), exception).finish();
      throw exception;
    }
  }
}
