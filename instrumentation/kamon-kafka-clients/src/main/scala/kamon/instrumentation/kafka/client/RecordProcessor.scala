package kamon.instrumentation.kafka.client

import java.time.Instant

import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.context.HasContext
import kamon.trace.Span
import org.apache.kafka.clients.consumer.ConsumerRecords

import scala.concurrent.duration._

private[kafka] object RecordProcessor {

  import scala.collection.JavaConverters._

  /**
   * Produces poll span (`operation=poll`) per each poll invocation which is then linked to per-record spans.
   * For each polled record a new consumer span (`operation=consumed-record`) is created as a child or
   * linked to it's bundled span (if any is present). Context (either new or inbound) containing consumer
   * span is then propagated with the record via `HasContext` mixin
   */
  def process[V, K](startTime: Instant, clientId: String, groupId: String, records: ConsumerRecords[K, V]): ConsumerRecords[K, V] = {
    val settings = KafkaInstrumentation.settings

    if (!records.isEmpty) {
      val pollSpan = Kamon.consumerSpanBuilder("poll", "kafka.consumer")
        .tag("kafka.consumer.record-count", records.count())
        .tag("kafka.groupId", groupId)
        .tag("kafka.clientId", clientId)
        .tag("kafka.partitions", records.partitions().asScala.map(_.partition()).mkString(","))
        .tag("kafka.topics", records.partitions().asScala.map(_.topic()).toSet.mkString(","))
        .start(startTime)

      pollSpan.finish()

      records.iterator().asScala.foreach { record =>
        val header = Option(record.headers.lastHeader("kamon-context"))

        val sendingContext = header.map { h =>
          ContextSerializationHelper.fromByteArray(h.value())
        }.getOrElse(Context.Empty)

        val spanBuilder = Kamon.consumerSpanBuilder("consumed-record", "kafka.consumer")
          .tag("kafka.groupId", groupId)
          .tag("kafka.clientId", clientId)
          .tag("kafka.topic", record.topic())
          .tag("kafka.partition", record.partition())
          .tag("kafka.offset", record.offset)
          .tag("kafka.timestamp", record.timestamp())
          .tag("kafka.timestampType", record.timestampType.name)

        // Key could be optional ... see tests
        Option(record.key()).foreach(k => spanBuilder.tag("kafka.key", record.key().toString)) //TODO unsure of toString behaviour here

        if (settings.continueTraceOnConsumer)
          spanBuilder.asChildOf(sendingContext.get(Span.Key))
        else
          spanBuilder.link(sendingContext.get(Span.Key), Span.Link.Kind.FollowsFrom)

        // Link new span also to polling span
        spanBuilder.link(pollSpan, Span.Link.Kind.FollowsFrom)

        val consumedRecordSpan = if (settings.useDelayedSpans)
          // Kafka's timestamp is expressed in millis, convert to nanos => this might spoil precision here ...
          spanBuilder.delay(Kamon.clock().toInstant(record.timestamp().millis.toNanos)).start(startTime)
        else
          spanBuilder.start(startTime)

        // Complete the span and store it on the record (using the HasSpan mixin)
        consumedRecordSpan.finish

        record
          .asInstanceOf[HasContext]
          .setContext(sendingContext.withEntry(Span.Key, consumedRecordSpan))
      }
    }

    records
  }
}
