package kamon.instrumentation.kafka.testutil

import net.manub.embeddedkafka.EmbeddedKafka

import scala.util.Random

trait TestTopicScope {
  val testTopicName = s"test-topic-${Random.nextLong()}"
  EmbeddedKafka.createCustomTopic(topic = testTopicName)
}
