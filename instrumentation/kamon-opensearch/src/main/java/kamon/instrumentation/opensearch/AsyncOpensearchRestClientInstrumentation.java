/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation.opensearch;

import kamon.Kamon;
import kamon.trace.Span;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import org.opensearch.client.Request;
import org.opensearch.client.ResponseListener;

public class AsyncOpensearchRestClientInstrumentation {

    @Advice.OnMethodEnter
    public static void enter(
            @Advice.Argument(0) Request request,
            @Advice.Argument(value = 1, readOnly = false) ResponseListener responseListener) {
        final String operationName =
                RequestNameConverter.convert(
                        HighLevelOpensearchClientInstrumentation.requestClassName.get(),
                        "AsyncRequest");

        Span span = Kamon.clientSpanBuilder(operationName, "opensearch.client")
                .tag("opensearch.http.endpoint", request.getEndpoint())
                .tag("opensearch.http.method", request.getMethod())
                .start();

        RequestSizeHistogram.record(request.getEntity());
        responseListener = new InstrumentedListener(responseListener, span);
    }
}
