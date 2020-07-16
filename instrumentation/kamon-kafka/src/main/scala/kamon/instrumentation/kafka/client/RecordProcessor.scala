package kamon.instrumentation.kafka.client

import java.time.Instant

import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.kafka.client.ConsumedRecordData.ConsumerInfo
import org.apache.kafka.clients.consumer.ConsumerRecords

private[kafka] object RecordProcessor {

  import scala.collection.JavaConverters._

  /**
   * Produces poll span (`operation=poll`) per each poll invocation which is then linked to per-record spans.
   * For each polled record a new consumer span (`operation=consumed-record`) is created as a child or
   * linked to it's bundled span (if any is present). Context (either new or inbound) containing consumer
   * span is then propagated with the record via `HasContext` mixin
   */
  def process[V, K](startTime: Instant, clientId: String, groupId: String, records: ConsumerRecords[K, V]): ConsumerRecords[K, V] = {

    if (!records.isEmpty) {
      val consumerInfo = ConsumerInfo(groupId, clientId)

      records.iterator().asScala.foreach(record => {
        val header = Option(record.headers.lastHeader(KafkaInstrumentation.Keys.ContextHeader))

        val incomingContext = header.map { h =>
          ContextSerializationHelper.fromByteArray(h.value())
        }.getOrElse(Context.Empty)

        record.asInstanceOf[ConsumedRecordData].set(
          incomingContext,
          Kamon.clock().nanosSince(startTime),
          consumerInfo
        )
      })
    }

    records
  }
}
