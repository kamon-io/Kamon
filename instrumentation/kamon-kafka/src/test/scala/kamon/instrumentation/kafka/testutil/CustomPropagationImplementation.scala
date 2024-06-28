package kamon.instrumentation.kafka.testutil

import kamon.context.Context
import kamon.instrumentation.kafka.client.KafkaPropagator
import kamon.trace.Trace.SamplingDecision
import kamon.trace.{Identifier, Span, Trace}

import org.apache.kafka.common.header.{Headers => KafkaHeaders}

class CustomPropagationImplementation extends KafkaPropagator {

  override def read(medium: KafkaHeaders, context: Context): Context = {

    val contextWithParent = for {
      traceId <- Option(medium.lastHeader("x-trace-id")).map(_.value())
      traceIdStr = new String(traceId, "utf-8")
      spanId <- Option(medium.lastHeader("x-span-id")).map(_.value())
      spanIdStr = new String(spanId, "utf-8")
      sampled <- Option(medium.lastHeader("x-trace-sampled")).map(_.value()).map {
        case Array(1) => SamplingDecision.Sample
        case Array(0) => SamplingDecision.DoNotSample
        case _        => SamplingDecision.Unknown
      }
      span =
        Span.Remote(Identifier(spanIdStr, spanId), Identifier.Empty, Trace(Identifier(traceIdStr, traceId), sampled))
    } yield context.withEntry(Span.Key, span)

    contextWithParent.getOrElse(context)
  }

  override def write(context: Context, medium: KafkaHeaders): Unit = {
    val span = context.get(Span.Key)

    if (span != Span.Empty) {
      medium.add("x-trace-id", span.trace.id.string.getBytes("utf-8"))
      medium.add("x-span-id", span.id.string.getBytes("utf-8"))
      medium.add("x-trace-sampled", if (span.trace.samplingDecision == SamplingDecision.Sample) Array(1) else Array(0))
    }
  }
}
