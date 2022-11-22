package kamon.instrumentation.kafka.sensor

import kamon.Kamon
import kamon.instrumentation.kafka.testutil.TestTopicScope
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpec}

class SensorInstrumentationSpec extends WordSpec
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll
  with EmbeddedKafka
  with TestTopicScope {

  // increase zk connection timeout to avoid failing tests in "slow" environments
  implicit val defaultConfig: EmbeddedKafkaConfig =
    EmbeddedKafkaConfig.apply(customBrokerProperties = EmbeddedKafkaConfig.apply().customBrokerProperties
      + ("zookeeper.connection.timeout.ms" -> "20000")
      + ("auto.create.topics.enable" -> "false")
    )

  override protected def beforeAll(): Unit = EmbeddedKafka.start()(defaultConfig)

  override def afterAll(): Unit = EmbeddedKafka.stop()

  "The Kafka Sensor Instrumentation" should {

    "export gauge metric" in new TestTopicScope {
      publishStringMessageToKafka(testTopicName, "message")(defaultConfig)

      Kamon.status().metricRegistry().metrics.size should be > 0
      Kamon.status().metricRegistry().metrics.exists(metric => metric.name.contains("topic")) should be(true)
      // TODO remove after generating appropriate metrics for each sensor
      Kamon.status().metricRegistry().metrics.map(_.name).foreach(println)
    }
  }
}
