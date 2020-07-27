package kamon.instrumentation.kafka.internal

import kamon.Kamon
import kamon.instrumentation.kafka.testutil.{SpanReportingTestScope, TestSpanReporting, TestTopicScope}
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.CommonClientConfigs
import org.scalatest._

class KafkaInternalInstrumentationSpec extends WordSpec
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll
  with EmbeddedKafka
  with TestTopicScope
  with TestSpanReporting {

  // increase zk connection timeout to avoid failing tests in "slow" environments
  implicit val defaultConfig: EmbeddedKafkaConfig =
    EmbeddedKafkaConfig.apply(customBrokerProperties = EmbeddedKafkaConfig.apply().customBrokerProperties
      + ("zookeeper.connection.timeout.ms" -> "20000")
      + ("auto.create.topics.enable" -> "false")
      + (CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG -> KafkaInternalInstrumentation.kafkaReporterValue())
    )

  override protected def beforeAll(): Unit = EmbeddedKafka.start()(defaultConfig)
  override def afterAll(): Unit = EmbeddedKafka.stop()

  "The Kafka Internal Instrumentation" should {
    "have the reporter class value" in {
      KafkaInternalInstrumentation.kafkaReporterValue() should be("kamon.instrumentation.kafka.internal.KafkaInternalInstrumentation")
    }

    "export base metric" in new SpanReportingTestScope(reporter) with TestTopicScope {
      publishStringMessageToKafka(testTopicName, "message")(defaultConfig)

      Kamon.status().metricRegistry().metrics.size should be > 0
      Kamon.status().metricRegistry().metrics.exists(metric => metric.name.contains("kafka")) should be (true)
      Kamon.status().metricRegistry().metrics.exists(metric => metric.name.equals("kafka_controller_channel_metrics_connection_count")) should be (true)
    }
  }
}
