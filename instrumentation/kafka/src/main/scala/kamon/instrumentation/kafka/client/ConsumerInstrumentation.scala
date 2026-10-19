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

import java.time.{Duration, Instant}
import kamon.context.Context
import kamon.instrumentation.kafka.client.ConsumedRecordData.ConsumerInfo
import kamon.instrumentation.kafka.client.advisor.{
  PollMethodAdvisor,
  PollMethodAdvisor_3_7_0_and_up_Async,
  PollMethodAdvisor_3_7_0_and_up_Legacy
}
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatchers.{declaresField, hasType, named}

class ConsumerInstrumentation extends InstrumentationBuilder {

  /**
    * Instruments org.apache.kafka.clients.consumer.KafkaConsumer::poll(Long)
    * Kafka version < 2.3
    */
  onTypesMatching(named("org.apache.kafka.clients.consumer.KafkaConsumer").and(declaresField(named("groupId"))))
    .advise(method("poll").and(withArgument(0, classOf[Long])), classOf[PollMethodAdvisor])

  /**
    * Instruments org.apache.kafka.clients.consumer.KafkaConsumer::poll(Duration)
    * Kafka version >= 2.3 < 3.7
    */
  onTypesMatching(named("org.apache.kafka.clients.consumer.KafkaConsumer").and(declaresField(named("groupId"))))
    .advise(method("poll").and(withArgument(0, classOf[Duration])), classOf[PollMethodAdvisor])

  /**
    * Instruments org.apache.kafka.clients.consumer.KafkaConsumer::poll(Duration)
    * Kafka version >= 3.7
    */
  onTypesMatching(
    named("org.apache.kafka.clients.consumer.internals.AsyncKafkaConsumer").and(declaresField(named("groupMetadata")))
  )
    .advise(method("poll").and(withArgument(0, classOf[Duration])), classOf[PollMethodAdvisor_3_7_0_and_up_Async])

  /**
    * Instruments org.apache.kafka.clients.consumer.KafkaConsumer::poll(Duration)
    * Kafka version >= 3.7
    */
  onTypesMatching(
    named("org.apache.kafka.clients.consumer.internals.LegacyKafkaConsumer").and(declaresField(named("coordinator")))
  )
    .advise(method("poll").and(withArgument(0, classOf[Duration])), classOf[PollMethodAdvisor_3_7_0_and_up_Legacy])

  /**
    * Instruments org.apache.kafka.clients.consumer.ConsumerRecord with the HasSpan mixin in order
    * to make the span available as parent for down stream operations
    */
  onSubTypesOf("org.apache.kafka.clients.consumer.ConsumerRecord")
    .mixin(classOf[ConsumedRecordData.Mixin])

}

trait ConsumedRecordData {
  def nanosSincePollStart(): Long
  def consumerInfo(): ConsumerInfo
  def set(nanosSincePollStart: Long, consumerInfo: ConsumerInfo): Unit
}

object ConsumedRecordData {

  case class ConsumerInfo(groupId: Option[String], clientId: String)

  class Mixin extends ConsumedRecordData {
    private var _nanosSincePollStart: Long = _
    private var _consumerInfo: ConsumerInfo = _

    override def nanosSincePollStart(): Long =
      _nanosSincePollStart

    override def consumerInfo(): ConsumerInfo =
      _consumerInfo

    override def set(nanosSincePollStart: Long, consumerInfo: ConsumerInfo): Unit = {
      _nanosSincePollStart = nanosSincePollStart
      _consumerInfo = consumerInfo
    }
  }
}
