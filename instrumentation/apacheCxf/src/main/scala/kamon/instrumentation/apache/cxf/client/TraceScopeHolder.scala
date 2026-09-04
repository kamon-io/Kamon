package kamon.instrumentation.apache.cxf.client

import kamon.context.Storage.Scope
import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler
import org.apache.cxf.message.Message

import java.io.Closeable

private class TraceScopeHolder(val traceScope: Option[TraceScope], val detached: Boolean = false) extends Serializable

private case class TraceScope(handler: RequestHandler[Message], scope: Option[Scope]) extends Closeable {

  override def close(): Unit = {
    if (handler != null && handler.span != null) handler.span.finish()
    if (scope.nonEmpty) scope.get.close()
  }
}
