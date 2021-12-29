package kamon.instrumentation.finagle.client

import com.twitter.finagle.http.Status
import com.twitter.util.Time
import kamon.trace.Span

import java.net.InetSocketAddress

/**
 * Unique tags and value constants added to Kamon Finagle spans.
 *
 * As a general rule metric tags should only be for tags with low cardinality.
 */
private[finagle] object Tags {

  object Keys {
    val ResourceName = "resource.name"
    val SpanKind = "span.kind"
    val SpanType = "span.type"
    val ErrorType = "error.type"
    val PeerPort = "peer.port"
    val PeerHostIPV4 = "peer.ipv4"
    val PeerHostname = "peer.hostname"
    val HttpStatusCategory = "http.status_category"
  }

  object Values {
    val ClientSpanKind = "client"
    val HttpSpanType = "http"
  }

  import Keys._
  import Values._

  def setHttpClientTags(span: Span): Unit = {
    span.tag(SpanKind, ClientSpanKind)
    span.tag(SpanType, HttpSpanType)
  }

  def setHttpResponseCategory(span: Span, status: Status): Unit =
    span.tagMetrics(HttpStatusCategory, categorize(status))

  private def categorize(status: Status): String =
    if (status.code >= 100 && status.code < 200) "1xx"
    else if (status.code >= 200 && status.code < 300) "2xx"
    else if (status.code >= 300 && status.code < 400) "3xx"
    else if (status.code >= 400 && status.code < 500) "4xx"
    else if (status.code >= 500 && status.code < 600) "5xx"
    else "unknown"

  def mark(span: Span, event: String, time: Time): Unit = span.mark(event, time.toInstant)

  def fail(span: Span, event: String, msg: String, time: Time): Unit = {
    span.tag(Tags.Keys.ErrorType, event)
    span.fail(msg)
  }

  def setPeer(span: Span, addr: InetSocketAddress): Unit = {
    span.tag(Tags.Keys.PeerPort, addr.getPort)
    span.tag(Tags.Keys.PeerHostIPV4, addr.getAddress.getHostAddress)
    span.tag(Tags.Keys.PeerHostname, addr.getHostString)
  }
}
