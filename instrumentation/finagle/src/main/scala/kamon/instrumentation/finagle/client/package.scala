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

package kamon.instrumentation.finagle

import com.twitter.finagle.Http
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.filter.Export
import com.twitter.finagle.tracing.TraceInitializerFilter

package object client {
  implicit final class HttpClientOps(private val client: Http.Client) extends AnyVal {
    def withKamonTracing: Http.Client = {
      client
        .withTracer(new KamonFinagleTracer)
        .withStack { stack =>
          stack.replace(TraceInitializerFilter.role, new SpanInitializer[Request, Response])
            .insertAfter(Export.httpTracingFilterRole, new ClientStatusTraceFilter[Request, Response]())
            // HTTP request tags (http.method, url) are added by Kamon `HttpClientInstrumentation`
            .remove(Export.httpTracingFilterRole)

        }
    }
  }
}
