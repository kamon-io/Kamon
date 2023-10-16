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

package kamon.instrumentation.armeria.server;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;
import io.netty.util.AttributeKey;
import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage;
import kamon.instrumentation.armeria.converters.KamonArmeriaMessageConverter;
import kamon.instrumentation.http.HttpServerInstrumentation;

import java.util.Map;

public class ArmeriaHttpServerDecorator extends SimpleDecoratingHttpService {
  public static final AttributeKey<HttpServerInstrumentation.RequestHandler> REQUEST_HANDLER_TRACE_KEY =
          AttributeKey.valueOf(HttpServerInstrumentation.RequestHandler.class, "REQUEST_HANDLER_TRACE");

  private final Map<Integer, HttpServerInstrumentation> serverInstrumentationMap;

  public ArmeriaHttpServerDecorator(HttpService delegate, Map<Integer, HttpServerInstrumentation> serverInstrumentations) {
    super(delegate);
    this.serverInstrumentationMap = serverInstrumentations;
  }

  @Override
  public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
    final HttpServerInstrumentation httpServerInstrumentation = serverInstrumentationMap.get(req.uri().getPort());

    if (httpServerInstrumentation != null) {

      final HttpServerInstrumentation.RequestHandler requestHandler =
              httpServerInstrumentation.createHandler(KamonArmeriaMessageConverter.toRequest(req));

      ctx.log()
              .whenComplete()
              .thenAccept(log -> {
                final Context context = ctx.attr(REQUEST_HANDLER_TRACE_KEY).context();
                /**
                 * This is true only when no configured Route was matched.
                 * Cases where the route is fallback should be managed here {@link kamon.instrumentation.armeria.server.HandleNotFoundMethodAdvisor#around}
                 */
                if (!ctx.config().route().isFallback()) {
                  requestHandler.span().name(ctx.config().route().patternString());
                }

                requestHandler.buildResponse(KamonArmeriaMessageConverter.toResponse(log), context);
                requestHandler.responseSent();
              });

      try (Storage.Scope ignored = Kamon.storeContext(requestHandler.context())) {
        ctx.setAttr(REQUEST_HANDLER_TRACE_KEY, requestHandler);
        return unwrap().serve(ctx, req);
      }
    } else {
      return unwrap().serve(ctx, req);
    }

  }

}


