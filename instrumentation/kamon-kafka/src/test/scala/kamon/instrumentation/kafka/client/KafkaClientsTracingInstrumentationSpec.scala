/* =========================================================================================
 * Copyright © 2013-2019 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.instrumentation.kafka.testutil.{SpanReportingTestScope, TestSpanReporting, TestTopicScope}
import kamon.tag.Lookups._
import kamon.testkit.{InitAndStopKamonAfterAll, Reconfigure}
import kamon.trace.Span
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, OptionValues}
import scala.util.Try

import org.scalatest.concurrent.PatienceConfiguration.Timeout

class KafkaClientsTracingInstrumentationSpec extends AnyWordSpec with Matchers
  with Eventually
  with SpanSugar
  with BeforeAndAfter
  with InitAndStopKamonAfterAll
  with EmbeddedKafka
  with Reconfigure
  with OptionValues
  with TestSpanReporting {

  // increase zk connection timeout to avoid failing tests in "slow" environments
  implicit val defaultConfig: EmbeddedKafkaConfig =
    EmbeddedKafkaConfig.apply(customBrokerProperties = EmbeddedKafkaConfig.apply().customBrokerProperties
      + ("zookeeper.connection.timeout.ms" -> "20000")
      + ("auto.create.topics.enable" -> "false")
    )

  implicit val stringDeser: Deserializer[String] = new StringDeserializer
  implicit val patienceConfigTimeout: Timeout = timeout(20 seconds)

  override def beforeAll(): Unit = {
    super.beforeAll()
    enableFastSpanFlushing()
    sampleAlways()
    EmbeddedKafka.start()(defaultConfig)
  }

  override def afterAll(): Unit = {
    EmbeddedKafka.stop()
    super.afterAll()
  }

  "The Kafka Clients Tracing Instrumentation" should {
    "create a Producer Span when publish a message" in new SpanReportingTestScope(reporter) with TestTopicScope {
      publishStringMessageToKafka(testTopicName, "Hello world!!!!!")
      awaitNumReportedSpans(1)

      assertReportedSpan(_.operationName == "producer.send") { span =>
        span.metricTags.get(plain("component")) shouldBe "kafka.producer"
        span.metricTags.get(plain("span.kind")) shouldBe "producer"
        span.tags.get(plain("kafka.topic")) shouldBe testTopicName
        span.tags.get(plain("kafka.key")) shouldBe KafkaInstrumentation.Keys.Null
        span.tags.get(plainLong("kafka.partition")) shouldBe 0L
      }

      assertNoSpansReported()
    }

    "fail the produce span when producing fails" in new SpanReportingTestScope(reporter) {
      Try(publishStringMessageToKafka("non-existent-topic", "msg to noone"))

      awaitNumReportedSpans(1)

      assertReportedSpan(_.operationName == "producer.send") { span =>
        span.hasError should be(true)
      }
    }

    "create a Producer/Consumer Span when publish/consume a message" in new SpanReportingTestScope(reporter) with TestTopicScope {

      publishStringMessageToKafka(testTopicName, "Hello world!!!")
      val consumedRecord = consumeFirstRawRecord[String, String](testTopicName)
      consumedRecord.value() shouldBe "Hello world!!!"

      awaitNumReportedSpans(2)

      assertReportedSpan(_.operationName == "producer.send") { span =>
        span.metricTags.get(plain("component")) shouldBe "kafka.producer"
        span.metricTags.get(plain("span.kind")) shouldBe "producer"
        span.tags.get(plain("kafka.topic")) shouldBe testTopicName
        span.tags.get(plain("kafka.key")) shouldBe KafkaInstrumentation.Keys.Null
        span.tags.get(plainLong("kafka.partition")) shouldBe 0L
      }

      assertReportedSpan(_.operationName == "consumer.process") { span =>
        span.metricTags.get(plain("component")) shouldBe "kafka.consumer"
        span.metricTags.get(plain("span.kind")) shouldBe "consumer"
        span.tags.get(plain("kafka.topic")) shouldBe testTopicName
        span.tags.get(plain("kafka.client-id")) should not be empty
        span.tags.get(plain("kafka.group-id")) should not be empty
        span.tags.get(plainLong("kafka.partition")) shouldBe 0L
        span.tags.get(plainLong("kafka.timestamp")) shouldBe consumedRecord.timestamp()
        span.tags.get(plain("kafka.timestamp-type")) shouldBe consumedRecord.timestampType().name
      }
    }

    "create a Producer/Consumer Span when publish/consume a message without follow-strategy and expect a linked span" in new SpanReportingTestScope(reporter) with TestTopicScope {
      Kamon.reconfigure(ConfigFactory.parseString("kamon.instrumentation.kafka.client.tracing.continue-trace-on-consumer = false").withFallback(Kamon.config()))

      publishStringMessageToKafka(testTopicName, "Hello world!!!")
      consumeFirstRawRecord[String, String](testTopicName).value() shouldBe "Hello world!!!"

      awaitNumReportedSpans(2)

      var sendingSpan: Option[Span.Finished] = None
      assertReportedSpan(_.operationName == "producer.send") { span =>
        span.metricTags.get(plain("span.kind")) shouldBe "producer"
        span.metricTags.get(plain("component")) shouldBe "kafka.producer"
        span.tags.get(plain("kafka.client-id")) should not be empty
        span.tags.get(plain("kafka.topic")) shouldBe testTopicName
        span.tags.get(plain("kafka.key")) shouldBe KafkaInstrumentation.Keys.Null
        span.tags.get(plainLong("kafka.partition")) shouldBe 0L
        sendingSpan = Some(span)
      }

      assertReportedSpan(_.operationName == "consumer.process") { span =>
        span.metricTags.get(plain("span.kind")) shouldBe "consumer"
        span.metricTags.get(plain("component")) shouldBe "kafka.consumer"
        span.tags.get(plain("kafka.topic")) shouldBe testTopicName
        span.tags.get(plain("kafka.client-id")) should not be empty
        span.tags.get(plain("kafka.group-id")) should not be empty
        span.tags.get(plainLong("kafka.partition")) shouldBe 0L
        span.links should have size 1
        val sendinglinks = span.links.filter(_.trace.id == sendingSpan.get.trace.id)
        sendinglinks should have size 1
        sendinglinks.head.trace.id shouldBe sendingSpan.get.trace.id
        sendinglinks.head.spanId shouldBe sendingSpan.get.id
      }

      assertNoSpansReported()
    }

    "create a Producer/Consumer Span when publish/consume a message with delayed spans" in new SpanReportingTestScope(reporter) with TestTopicScope {
      Kamon.reconfigure(ConfigFactory.parseString(
        """
          |kamon.instrumentation.kafka.client.tracing.use-delayed-spans = true
          |kamon.instrumentation.kafka.client.tracing.continue-trace-on-consumer = false
      """.stripMargin).withFallback(Kamon.config()))
      KafkaInstrumentation.settings.useDelayedSpans shouldBe true

      publishStringMessageToKafka(testTopicName, "Hello world!!!")
      consumeFirstRawRecord[String, String](testTopicName).value() shouldBe "Hello world!!!"

      awaitNumReportedSpans(2)

      var sendingSpan: Option[Span.Finished] = None
      assertReportedSpan(_.operationName == "producer.send") { span =>
        span.metricTags.get(plain("span.kind")) shouldBe "producer"
        span.metricTags.get(plain("component")) shouldBe "kafka.producer"
        span.tags.get(plain("kafka.client-id")) should not be empty
        span.tags.get(plain("kafka.topic")) shouldBe testTopicName
        span.tags.get(plain("kafka.key")) shouldBe KafkaInstrumentation.Keys.Null
        span.tags.get(plainLong("kafka.partition")) shouldBe 0L
        sendingSpan = Some(span)
      }

      assertReportedSpan(_.operationName == "consumer.process") { span =>
        span.wasDelayed shouldBe true
        span.metricTags.get(plain("span.kind")) shouldBe "consumer"
        span.metricTags.get(plain("component")) shouldBe "kafka.consumer"
        span.tags.get(plain("kafka.topic")) shouldBe testTopicName
        span.tags.get(plain("kafka.client-id")) should not be empty
        span.tags.get(plain("kafka.group-id")) should not be empty
        span.tags.get(plainLong("kafka.partition")) shouldBe 0L
        span.links should have size 1
        val sendinglinks = span.links.filter(_.trace.id == sendingSpan.get.trace.id)
        sendinglinks should have size 1
        val link = sendinglinks.head
        link.trace.id shouldBe sendingSpan.get.trace.id
        link.spanId shouldBe sendingSpan.get.id
      }

      assertNoSpansReported()
    }

    "create a Producer/Consumer Span when publish/consume a message with w3c format" in new SpanReportingTestScope(reporter) with TestTopicScope {
      applyConfig("kamon.trace.identifier-scheme = double")
      applyConfig("kamon.instrumentation.kafka.client.tracing.propagator = w3c")

      publishStringMessageToKafka(testTopicName, "Hello world!!!")

      val consumedRecord = consumeFirstRawRecord[String, String](testTopicName)

      consumedRecord.headers().lastHeader("traceparent").value() should not be empty
      consumedRecord.headers().lastHeader("kctx") shouldBe null
      consumedRecord.value() shouldBe "Hello world!!!"

      awaitNumReportedSpans(2)

      var sendingSpan: Option[Span.Finished] = None
      assertReportedSpan(_.operationName == "producer.send") { span =>
        span.metricTags.get(plain("component")) shouldBe "kafka.producer"
        span.metricTags.get(plain("span.kind")) shouldBe "producer"
        span.tags.get(plain("kafka.topic")) shouldBe testTopicName
        span.tags.get(plain("kafka.key")) shouldBe KafkaInstrumentation.Keys.Null
        span.tags.get(plainLong("kafka.partition")) shouldBe 0L
        sendingSpan = Some(span)
      }

      assertReportedSpan(_.operationName == "consumer.process") { span =>
        span.metricTags.get(plain("component")) shouldBe "kafka.consumer"
        span.metricTags.get(plain("span.kind")) shouldBe "consumer"
        span.tags.get(plain("kafka.topic")) shouldBe testTopicName
        span.tags.get(plain("kafka.client-id")) should not be empty
        span.tags.get(plain("kafka.group-id")) should not be empty
        span.tags.get(plainLong("kafka.partition")) shouldBe 0L
        span.tags.get(plainLong("kafka.timestamp")) shouldBe consumedRecord.timestamp()
        span.tags.get(plain("kafka.timestamp-type")) shouldBe consumedRecord.timestampType().name
        span.trace.id shouldBe sendingSpan.get.trace.id
        span.parentId shouldBe sendingSpan.get.id
      }
    }
  }
}
