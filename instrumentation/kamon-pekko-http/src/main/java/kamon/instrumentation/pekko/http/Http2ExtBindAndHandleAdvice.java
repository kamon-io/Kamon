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
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import scala.Function1;
import scala.concurrent.Future;

public class Http2ExtBindAndHandleAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(@Advice.Argument(value = 0, readOnly = false) Function1<HttpRequest, Future<HttpResponse>> handler,
     @Advice.Argument(1) String iface,
     @Advice.Argument(2) Integer port) {

    FlowOpsMapAsyncAdvice.currentEndpoint.set(new FlowOpsMapAsyncAdvice.EndpointInfo(iface, port));
    handler = new Http2BlueprintInterceptor.HandlerWithEndpoint(iface, port, handler);
  }

  @Advice.OnMethodExit
  public static void onExit() {
    FlowOpsMapAsyncAdvice.currentEndpoint.remove();
  }
}
