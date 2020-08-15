/*
 * =========================================================================================
 * Copyright © 2013-2019 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */
package kamon.instrumentation.kafka.client

import java.time.{Duration, Instant}

import kamon.context.Context
import kamon.instrumentation.kafka.client.ConsumedRecordData.ConsumerInfo
import kamon.instrumentation.kafka.client.advisor.PollMethodAdvisor
import kanela.agent.api.instrumentation.InstrumentationBuilder

class ConsumerInstrumentation extends InstrumentationBuilder {
  /**
    * Instruments org.apache.kafka.clients.consumer.KafkaConsumer::poll(Long)
    * Kafka version < 2.3
    */
  onType("org.apache.kafka.clients.consumer.KafkaConsumer")
    .advise(method("poll").and(withArgument(0, classOf[Long])), classOf[PollMethodAdvisor])

  /**
    * Instruments org.apache.kafka.clients.consumer.KafkaConsumer::poll(Duration)
    * Kafka version >= 2.3
    */
  onType("org.apache.kafka.clients.consumer.KafkaConsumer")
    .advise(method("poll").and(withArgument(0, classOf[Duration])), classOf[PollMethodAdvisor])

  /**
    * Instruments org.apache.kafka.clients.consumer.ConsumerRecord with the HasSpan mixin in order
    * to make the span available as parent for down stream operations
    */
  onSubTypesOf("org.apache.kafka.clients.consumer.ConsumerRecord")
    .mixin(classOf[ConsumedRecordData.Mixin])

}

trait ConsumedRecordData {
  def incomingContext(): Context
  def nanosSincePollStart(): Long
  def consumerInfo(): ConsumerInfo
  def set(incomingContext: Context, nanosSincePollStart: Long, consumerInfo: ConsumerInfo)
}

object ConsumedRecordData {

  case class ConsumerInfo(groupId: Option[String], clientId: String)

  class Mixin extends ConsumedRecordData {
    private var _incomingContext: Context = _
    private var _nanosSincePollStart: Long = _
    private var _consumerInfo: ConsumerInfo = _

    override def incomingContext(): Context =
      _incomingContext

    override def nanosSincePollStart(): Long =
      _nanosSincePollStart

    override def consumerInfo(): ConsumerInfo =
      _consumerInfo

    override def set(incomingContext: Context, nanosSincePollStart: Long, consumerInfo: ConsumerInfo): Unit = {
      _incomingContext = incomingContext
      _nanosSincePollStart = nanosSincePollStart
      _consumerInfo = consumerInfo
    }
  }
}

