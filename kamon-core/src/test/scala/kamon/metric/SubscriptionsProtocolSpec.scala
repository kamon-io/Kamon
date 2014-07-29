package kamon.metric

import akka.actor._
import akka.testkit.{ TestProbe, ImplicitSender, TestKitBase }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.Subscriptions.TickMetricSnapshot
import org.scalatest.{ Matchers, WordSpecLike }
import scala.concurrent.duration._

class SubscriptionsProtocolSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit def self = testActor
  implicit lazy val system: ActorSystem = ActorSystem("subscriptions-protocol-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  tick-interval = 1 hour
      |}
    """.stripMargin))

  val metricsExtension = Kamon(Metrics)(system)
  import metricsExtension.{ register, subscribe, unsubscribe }

  "the Subscriptions messaging protocol" should {
    "allow subscribing for a single tick" in {
      val subscriber = TestProbe()
      register(TraceMetrics("one-shot"), TraceMetrics.Factory)
      subscribe(TraceMetrics, "one-shot", subscriber.ref, permanently = false)

      metricsExtension.subscriptions ! Subscriptions.FlushMetrics
      val tickSnapshot = subscriber.expectMsgType[TickMetricSnapshot]

      tickSnapshot.metrics.size should be(1)
      tickSnapshot.metrics.keys should contain(TraceMetrics("one-shot"))

      metricsExtension.subscriptions ! Subscriptions.FlushMetrics
      subscriber.expectNoMsg(1 second)
    }

    "allow subscribing permanently to a metric" in {
      val subscriber = TestProbe()
      register(TraceMetrics("permanent"), TraceMetrics.Factory)
      subscribe(TraceMetrics, "permanent", subscriber.ref, permanently = true)

      for (repetition ← 1 to 5) {
        metricsExtension.subscriptions ! Subscriptions.FlushMetrics
        val tickSnapshot = subscriber.expectMsgType[TickMetricSnapshot]

        tickSnapshot.metrics.size should be(1)
        tickSnapshot.metrics.keys should contain(TraceMetrics("permanent"))
        subscriber.expectNoMsg(1 second)
      }
    }

    "allow subscribing to metrics matching a glob pattern" in {
      val subscriber = TestProbe()
      register(TraceMetrics("include-one"), TraceMetrics.Factory)
      register(TraceMetrics("exclude-two"), TraceMetrics.Factory)
      register(TraceMetrics("include-three"), TraceMetrics.Factory)
      subscribe(TraceMetrics, "include-*", subscriber.ref, permanently = true)

      for (repetition ← 1 to 5) {
        metricsExtension.subscriptions ! Subscriptions.FlushMetrics
        val tickSnapshot = subscriber.expectMsgType[TickMetricSnapshot]

        tickSnapshot.metrics.size should be(2)
        tickSnapshot.metrics.keys should contain(TraceMetrics("include-one"))
        tickSnapshot.metrics.keys should contain(TraceMetrics("include-three"))
        subscriber.expectNoMsg(1 second)
      }
    }

    "send a single TickMetricSnapshot to each subscriber, even if subscribed multiple times" in {
      val subscriber = TestProbe()
      register(TraceMetrics("include-one"), TraceMetrics.Factory)
      register(TraceMetrics("exclude-two"), TraceMetrics.Factory)
      register(TraceMetrics("include-three"), TraceMetrics.Factory)
      subscribe(TraceMetrics, "include-one", subscriber.ref, permanently = true)
      subscribe(TraceMetrics, "include-three", subscriber.ref, permanently = true)

      for (repetition ← 1 to 5) {
        metricsExtension.subscriptions ! Subscriptions.FlushMetrics
        val tickSnapshot = subscriber.expectMsgType[TickMetricSnapshot]

        tickSnapshot.metrics.size should be(2)
        tickSnapshot.metrics.keys should contain(TraceMetrics("include-one"))
        tickSnapshot.metrics.keys should contain(TraceMetrics("include-three"))
      }
    }

    "allow un-subscribing a subscriber" in {
      val subscriber = TestProbe()
      register(TraceMetrics("one-shot"), TraceMetrics.Factory)
      subscribe(TraceMetrics, "one-shot", subscriber.ref, permanently = true)

      metricsExtension.subscriptions ! Subscriptions.FlushMetrics
      val tickSnapshot = subscriber.expectMsgType[TickMetricSnapshot]
      tickSnapshot.metrics.size should be(1)
      tickSnapshot.metrics.keys should contain(TraceMetrics("one-shot"))

      unsubscribe(subscriber.ref)

      metricsExtension.subscriptions ! Subscriptions.FlushMetrics
      subscriber.expectNoMsg(1 second)
    }

    "watch all subscribers and un-subscribe them if they die" in {
      val subscriber = TestProbe()
      val forwarderSubscriber = system.actorOf(Props(new ForwarderSubscriber(subscriber.ref)))
      watch(forwarderSubscriber)
      register(TraceMetrics("one-shot"), TraceMetrics.Factory)
      subscribe(TraceMetrics, "one-shot", forwarderSubscriber, permanently = true)

      metricsExtension.subscriptions ! Subscriptions.FlushMetrics
      val tickSnapshot = subscriber.expectMsgType[TickMetricSnapshot]
      tickSnapshot.metrics.size should be(1)
      tickSnapshot.metrics.keys should contain(TraceMetrics("one-shot"))

      forwarderSubscriber ! PoisonPill
      expectTerminated(forwarderSubscriber)

      metricsExtension.subscriptions ! Subscriptions.FlushMetrics
      metricsExtension.subscriptions ! Subscriptions.FlushMetrics
      metricsExtension.subscriptions ! Subscriptions.FlushMetrics
      metricsExtension.subscriptions ! Subscriptions.FlushMetrics
      subscriber.expectNoMsg(2 seconds)
    }
  }
}

class ForwarderSubscriber(target: ActorRef) extends Actor {
  def receive = {
    case anything ⇒ target.forward(anything)
  }
}
