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

package kamon.instrumentation.kafka.client.advisor;

import kamon.Kamon;
import kamon.context.Context;
import kamon.instrumentation.context.HasContext;
import kamon.instrumentation.kafka.client.KafkaInstrumentation;
import kamon.instrumentation.kafka.client.ContextSerializationHelper;
import kamon.instrumentation.kafka.client.ProducerCallback;
import kamon.trace.Span;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * Producer Instrumentation
 */
public class SendMethodAdvisor {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) ProducerRecord record,
                               @Advice.Argument(value = 1, readOnly = false) Callback callback,
                               @Advice.FieldValue("clientId") String clientId) {
        Context recordContext = ((HasContext) record).context();

        if(!recordContext.get(Span.Key()).isEmpty() || KafkaInstrumentation.settings().startTraceOnProducer()) {
            String nullKey = KafkaInstrumentation.Keys$.MODULE$.Null();
            String topic = record.topic() == null ? nullKey : record.topic();
            String key = record.key() == null ? nullKey : record.key().toString();

            Span span = Kamon.producerSpanBuilder("producer.send", "kafka.producer")
                    .asChildOf(recordContext.get(Span.Key()))
                    .tag("kafka.topic", topic)
                    .tag("kafka.client-id", clientId)
                    .tag("kafka.key", key)
                    .start();

            Context ctx = recordContext.withEntry(Span.Key(), span);
            KafkaInstrumentation.settings().propagator().write(ctx, record.headers());
            
            callback = new ProducerCallback(callback, span, ctx);
        }
    }
}
