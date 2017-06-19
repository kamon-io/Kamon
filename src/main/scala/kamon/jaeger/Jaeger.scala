package kamon.jaeger

import java.util

import com.typesafe.config.Config
import com.uber.jaeger.agent.thrift.Agent
import com.uber.jaeger.thriftjava.{Batch, Process, Tag, TagType, Span => JaegerSpan}
import kamon.{Kamon, SpanReporter}
import kamon.trace.Span
import org.apache.thrift.protocol.TCompactProtocol

class Jaeger() extends SpanReporter {
  override def reconfigure(config: Config): Unit = {}
  override def start(): Unit = {}
  override def stop(): Unit = {}

  val jaegerClient = new JaegerClient()


  override def reportSpans(spans: Seq[Span.CompletedSpan]): Unit = {
    spans.foreach(s => jaegerClient.sendSpan(s))
  }
}


class JaegerClient() {
  import scala.collection.JavaConverters._

  val transport = TUDPTransport.NewTUDPClient("jaeger", 5775)
  val client = new Agent.Client(new TCompactProtocol(transport))
  val process = new Process(Kamon.environment.application)


  def sendSpan(span: Span.CompletedSpan): Unit = {
    client.emitBatch(new Batch(process, Seq(convertSpan(span)).asJava))
  }

  private def convertSpan(span: Span.CompletedSpan): JaegerSpan = {
    val convertedSpan = new JaegerSpan(
      span.context.traceID,
      0L,
      span.context.spanID,
      span.context.parentID,
      span.operationName,
      0,
      span.startTimestampMicros,
      span.endTimestampMicros - span.startTimestampMicros
    )

    convertedSpan.setTags(new util.ArrayList[Tag](span.tags.size))
    span.tags.foreach {
      case (k, v) =>
        val tag = new Tag(k, TagType.STRING)
        tag.setVStr(v)
        convertedSpan.tags.add(tag)
    }

    convertedSpan
  }
}
