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

package kamon.instrumentation.kafka.client

import java.time.Instant
import java.util.Optional

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
  def process[V, K](startTime: Instant, clientId: String, groupId: AnyRef, records: ConsumerRecords[K, V]): ConsumerRecords[K, V] = {

    if (!records.isEmpty) {
      val consumerInfo = ConsumerInfo(resolve(groupId), clientId)

      records.iterator().asScala.foreach(record => {
        record.asInstanceOf[ConsumedRecordData].set(
          Kamon.clock().nanosSince(startTime),
          consumerInfo
        )
      })
    }

    records
  }

  /**
    * In order to be backward compatible we need check the groupId field.
    *
    * KafkaConsumer which versions < 2.5 relies on internal groupId: String and higher versions in Optional[String].
    */
  private def resolve(groupId: AnyRef): Option[String] = groupId match {
      case opt: Optional[String] => if (opt.isPresent) Some(opt.get()) else None
      case value: String => Option(value)
      case _ => None
    }
}
