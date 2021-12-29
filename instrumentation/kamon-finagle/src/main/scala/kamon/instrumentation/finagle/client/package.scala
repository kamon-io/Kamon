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
