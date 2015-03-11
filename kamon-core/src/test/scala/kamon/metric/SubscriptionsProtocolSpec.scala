/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.metric

import akka.actor._
import akka.testkit.{ TestProbe, ImplicitSender }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.testkit.BaseKamonSpec
import scala.concurrent.duration._

class SubscriptionsProtocolSpec extends BaseKamonSpec("subscriptions-protocol-spec") with ImplicitSender {
  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon.metric {
        |  tick-interval = 1 hour
        |}
      """.stripMargin)

  lazy val metricsModule = Kamon.metrics
  import metricsModule.{ entity, subscribe, unsubscribe }

  "the Subscriptions messaging protocol" should {
    "allow subscribing for a single tick" in {
      val subscriber = TestProbe()
      entity(TraceMetrics, "one-shot")
      subscribe("trace", "one-shot", subscriber.ref, permanently = false)

      flushSubscriptions()
      val tickSnapshot = subscriber.expectMsgType[TickMetricSnapshot]

      tickSnapshot.metrics.size should be(1)
      tickSnapshot.metrics.keys should contain(Entity("one-shot", "trace"))

      flushSubscriptions()
      subscriber.expectNoMsg(1 second)
    }

    "allow subscribing permanently to a metric" in {
      val subscriber = TestProbe()
      entity(TraceMetrics, "permanent")
      subscribe("trace", "permanent", subscriber.ref, permanently = true)

      for (repetition ← 1 to 5) {
        flushSubscriptions()
        val tickSnapshot = subscriber.expectMsgType[TickMetricSnapshot]

        tickSnapshot.metrics.size should be(1)
        tickSnapshot.metrics.keys should contain(Entity("permanent", "trace"))
      }
    }

    "allow subscribing to metrics matching a glob pattern" in {
      val subscriber = TestProbe()
      entity(TraceMetrics, "include-one")
      entity(TraceMetrics, "exclude-two")
      entity(TraceMetrics, "include-three")
      subscribe("trace", "include-*", subscriber.ref, permanently = true)

      for (repetition ← 1 to 5) {
        flushSubscriptions()
        val tickSnapshot = subscriber.expectMsgType[TickMetricSnapshot]

        tickSnapshot.metrics.size should be(2)
        tickSnapshot.metrics.keys should contain(Entity("include-one", "trace"))
        tickSnapshot.metrics.keys should contain(Entity("include-three", "trace"))
      }
    }

    "send a single TickMetricSnapshot to each subscriber, even if subscribed multiple times" in {
      val subscriber = TestProbe()
      entity(TraceMetrics, "include-one")
      entity(TraceMetrics, "exclude-two")
      entity(TraceMetrics, "include-three")
      subscribe("trace", "include-one", subscriber.ref, permanently = true)
      subscribe("trace", "include-three", subscriber.ref, permanently = true)

      for (repetition ← 1 to 5) {
        flushSubscriptions()
        val tickSnapshot = subscriber.expectMsgType[TickMetricSnapshot]

        tickSnapshot.metrics.size should be(2)
        tickSnapshot.metrics.keys should contain(Entity("include-one", "trace"))
        tickSnapshot.metrics.keys should contain(Entity("include-three", "trace"))
      }
    }

    "allow un-subscribing a subscriber" in {
      val subscriber = TestProbe()
      entity(TraceMetrics, "one-shot")
      subscribe("trace", "one-shot", subscriber.ref, permanently = true)

      flushSubscriptions()
      val tickSnapshot = subscriber.expectMsgType[TickMetricSnapshot]
      tickSnapshot.metrics.size should be(1)
      tickSnapshot.metrics.keys should contain(Entity("one-shot", "trace"))

      unsubscribe(subscriber.ref)

      flushSubscriptions()
      subscriber.expectNoMsg(1 second)
    }
  }

  def subscriptionsActor: ActorRef = {
    val listener = TestProbe()
    system.actorSelection("/user/kamon/kamon-metrics").tell(Identify(1), listener.ref)
    listener.expectMsgType[ActorIdentity].ref.get
  }
}

class ForwarderSubscriber(target: ActorRef) extends Actor {
  def receive = {
    case anything ⇒ target.forward(anything)
  }
}
