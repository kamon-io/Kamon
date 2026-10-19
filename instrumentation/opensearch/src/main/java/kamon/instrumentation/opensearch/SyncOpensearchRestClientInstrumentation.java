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

public class SyncOpensearchRestClientInstrumentation {

    @Advice.OnMethodEnter
    public static void enter(
            @Advice.Argument(0) Request request,
            @Advice.Local("span") Span span) {
        final String operationName = RequestNameConverter.convert(
                HighLevelOpensearchClientInstrumentation.requestClassName.get(),
                "SyncRequest");

        RequestSizeHistogram.record(request.getEntity());
        span = Kamon.clientSpanBuilder(operationName, "opensearch.client")
                .tag("opensearch.http.endpoint", request.getEndpoint())
                .tag("opensearch.http.method", request.getMethod())
                .start();
    }

    @Advice.OnMethodExit
    public static void exit(
            @Advice.Local("span") Span span) {
        span.finish();
    }
}
