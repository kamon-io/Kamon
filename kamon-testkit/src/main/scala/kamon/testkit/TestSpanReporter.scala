package kamon.testkit

import java.util.concurrent.LinkedBlockingQueue

import com.typesafe.config.Config
import kamon.SpanReporter
import kamon.trace.Span
import kamon.trace.Span.FinishedSpan

class TestSpanReporter() extends SpanReporter {
  import scala.collection.JavaConverters._
  private val reportedSpans = new LinkedBlockingQueue[FinishedSpan]()

  override def reportSpans(spans: Seq[Span.FinishedSpan]): Unit =
    reportedSpans.addAll(spans.asJava)

  def nextSpan(): Option[FinishedSpan] =
    Option(reportedSpans.poll())

  override def start(): Unit = {}
  override def stop(): Unit = {}
  override def reconfigure(config: Config): Unit = {}
}
