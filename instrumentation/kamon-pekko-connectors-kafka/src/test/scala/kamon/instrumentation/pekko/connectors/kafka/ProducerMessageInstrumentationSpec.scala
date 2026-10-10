/*
 *  ==========================================================================================
 *  Copyright © 2013-2026 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon.instrumentation.pekko.connectors.kafka

import java.util.concurrent.{ConcurrentLinkedQueue, Future => JFuture}

import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups.option
import org.apache.kafka.clients.producer.{Callback, MockProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.kafka.testkit.ConsumerResultFactory
import org.apache.pekko.kafka.{CommitterSettings, ProducerMessage, ProducerSettings}
import org.apache.pekko.kafka.scaladsl.Producer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

/**
  * Runs the producer stages against a Kafka MockProducer, so no Kafka broker is needed. The MockProducer records the
  * current Context when the stage calls send, which should be the Context captured when the message was created.
  */
class ProducerMessageInstrumentationSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with ScalaFutures
    with Eventually {

  implicit val system: ActorSystem = ActorSystem("producer-message-instrumentation-spec")
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(10, Seconds))

  "the Pekko Connectors Kafka producer instrumentation" should {
    "propagate the Context captured when a Message is created to the Kafka producer" in {
      val producer = new ContextCapturingProducer
      val message = Kamon.runWithContext(Context.of("message", "plain")) {
        ProducerMessage.single(new ProducerRecord[String, String]("topic", "key", "value"))
      }

      Source.single(message).via(Producer.flexiFlow(producerSettings(producer))).runWith(Sink.ignore).futureValue
      producer.sentWithTags shouldBe Seq(Some("plain"))
    }

    "propagate the Context captured when a MultiMessage is created to the Kafka producer" in {
      val producer = new ContextCapturingProducer
      val message = Kamon.runWithContext(Context.of("message", "multi")) {
        ProducerMessage.multi(Seq(
          new ProducerRecord[String, String]("topic", "key", "one"),
          new ProducerRecord[String, String]("topic", "key", "two")
        ))
      }

      Source.single(message).via(Producer.flexiFlow(producerSettings(producer))).runWith(Sink.ignore).futureValue
      producer.sentWithTags shouldBe Seq(Some("multi"), Some("multi"))
    }

    "propagate the Context captured when a Message is created to the Kafka producer in a committable sink" in {
      val producer = new ContextCapturingProducer
      val offset = ConsumerResultFactory.committableOffset("group", "topic", 0, 0L, "")
      val message = Kamon.runWithContext(Context.of("message", "committable")) {
        ProducerMessage.single(new ProducerRecord[String, String]("topic", "key", "value"), offset)
      }

      // The commit goes to a fake committer, so only wait until the record has been sent.
      Source.single(message).runWith(Producer.committableSink(producerSettings(producer), CommitterSettings(system)))
      eventually(producer.sentWithTags shouldBe Seq(Some("committable")))
    }
  }

  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  private def producerSettings(producer: ContextCapturingProducer): ProducerSettings[String, String] =
    ProducerSettings(system, new StringSerializer, new StringSerializer).withProducer(producer)

  private class ContextCapturingProducer
      extends MockProducer[String, String](true, new StringSerializer, new StringSerializer) {
    private val sentContexts = new ConcurrentLinkedQueue[Context]()

    def sentWithTags: Seq[Option[String]] =
      sentContexts.asScala.toSeq.map(_.getTag(option("message")))

    override def send(record: ProducerRecord[String, String], callback: Callback): JFuture[RecordMetadata] = {
      sentContexts.add(Kamon.currentContext())
      super.send(record, callback)
    }
  }
}
