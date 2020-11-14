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

package kamon.armeria.instrumentation.server;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;
import io.netty.util.AttributeKey;
import kamon.Kamon;
import kamon.armeria.instrumentation.converters.KamonArmeriaMessageConverter;
import kamon.context.Storage;
import kamon.instrumentation.http.HttpServerInstrumentation;

import java.net.InetSocketAddress;

public class ArmeriaHttpServerDecorator extends SimpleDecoratingHttpService {
  public static final AttributeKey<HttpServerInstrumentation.RequestHandler> REQUEST_HANDLER_TRACE_KEY =
      AttributeKey.valueOf(HttpServerInstrumentation.RequestHandler.class, "REQUEST_HANDLER_TRACE");


  private final HttpServerInstrumentation httpServerInstrumentation;
  private final String serverHost;

  public ArmeriaHttpServerDecorator(HttpService delegate, HttpServerInstrumentation httpServerInstrumentation, String serverHost) {
    super(delegate);
    this.serverHost = serverHost;
    this.httpServerInstrumentation = httpServerInstrumentation;
  }

  @Override
  public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
    //I'm not sure about ((InetSocketAddress) ctx.localAddress()), i need the request port
    // to check if this is the decorator that corresponds to this port
    if (httpServerInstrumentation.port() == ((InetSocketAddress) ctx.localAddress()).getPort()) {

      final HttpServerInstrumentation.RequestHandler requestHandler =
          httpServerInstrumentation.createHandler(KamonArmeriaMessageConverter.toRequest(req, serverHost, httpServerInstrumentation.port()));

      ctx.log()
          .whenComplete()
          .thenAccept(log -> {
            try (Storage.Scope scope = Kamon.storeContext(ctx.attr(REQUEST_HANDLER_TRACE_KEY).context())) {
              requestHandler.buildResponse(KamonArmeriaMessageConverter.toResponse(log), scope.context());
              requestHandler.responseSent();
            }
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


