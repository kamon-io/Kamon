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

import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.context.HasContext
import kamon.instrumentation.kafka.client.advisor.SendMethodAdvisor
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import org.apache.kafka.clients.producer.{Callback, RecordMetadata}

class ProducerInstrumentation extends InstrumentationBuilder {

  /**
    * Instruments "org.apache.kafka.clients.producer.KafkaProducer::Send()
    */
  onType("org.apache.kafka.clients.producer.KafkaProducer")
    .advise(method("doSend").and(takesArguments(2)), classOf[SendMethodAdvisor])

  onType("org.apache.kafka.clients.producer.ProducerRecord")
    .mixin(classOf[HasContext.VolatileMixinWithInitializer])
}

/**
  * Producer Callback Wrapper
  */
final class ProducerCallback(callback: Callback, sendingSpan: Span, context: Context) extends Callback {
  override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {

    try {
      if (exception != null)
        sendingSpan.fail(exception)
      else
        sendingSpan.tag("kafka.partition", metadata.partition())

      if (callback != null)
        Kamon.runWithContext(context)(callback.onCompletion(metadata, exception))
    } finally {
      sendingSpan.finish()
    }
  }
}
