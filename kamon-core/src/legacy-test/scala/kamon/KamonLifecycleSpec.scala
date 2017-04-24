package kamon

import akka.actor.ActorSystem
import akka.testkit.{TestKitBase, TestProbe}
import com.typesafe.config.ConfigFactory
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.{EntitySnapshot, SubscriptionsDispatcher}
import kamon.util.LazyActorRef
import org.scalatest.{Matchers, WordSpecLike}
import org.scalactic.TimesOnInt._

import scala.concurrent.duration._

class KamonLifecycleSpec extends TestKitBase with WordSpecLike with Matchers {
  override implicit lazy val system: ActorSystem = ActorSystem("kamon-lifecycle-spec")

  "The Kamon lifecycle" should {
    "allow Kamon to be used before it gets started" in {
      val someMetric = Kamon.metrics.histogram("allow-me-before-start")
    }

    "allow Kamon to be started/shutdown several times" in {
      10 times {
        Kamon.shutdown()
        Kamon.start()
        Kamon.start()
        Kamon.shutdown()
        Kamon.shutdown()
      }
    }

    "not dispatch subscriptions before Kamon startup" in {
      val subscriber = TestProbe()
      Kamon.metrics.histogram("only-after-startup").record(100)
      Kamon.metrics.subscribe("**", "**", subscriber.ref, permanently = true)

      flushSubscriptions()
      subscriber.expectNoMsg(300 millis)

      Kamon.metrics.histogram("only-after-startup").record(100)
      Kamon.start()
      flushSubscriptions()
      subscriber.expectMsgType[TickMetricSnapshot]
      Kamon.shutdown()
    }

    "not dispatch subscriptions after Kamon shutdown" in {
      val subscriber = TestProbe()
      Kamon.start()
      Kamon.metrics.histogram("only-before-shutdown").record(100)
      Kamon.metrics.subscribe("**", "**", subscriber.ref, permanently = true)

      flushSubscriptions()
      subscriber.expectMsgType[TickMetricSnapshot]

      Kamon.metrics.histogram("only-before-shutdown").record(100)
      Kamon.shutdown()
      Thread.sleep(500)
      flushSubscriptions()
      subscriber.expectNoMsg(300 millis)
    }

    "reconfigure filters after being started" in {
      val customConfig = ConfigFactory.parseString(
        """
          |kamon.metric.filters.histogram {
          |  includes = [ "**" ]
          |  excludes = ["untracked-histogram"]
          |}
        """.stripMargin
      )

      Kamon.metrics.shouldTrack("untracked-histogram", "histogram") shouldBe true
      Kamon.start(customConfig.withFallback(ConfigFactory.load()))
      Kamon.metrics.shouldTrack("untracked-histogram", "histogram") shouldBe false

    }
  }

  def takeSnapshotOf(name: String, category: String): EntitySnapshot = {
    val collectionContext = Kamon.metrics.buildDefaultCollectionContext
    val recorder = Kamon.metrics.find(name, category).get
    recorder.collect(collectionContext)
  }

  def flushSubscriptions(): Unit = {
    val subscriptionsField = Kamon.metrics.getClass.getDeclaredField("_subscriptions")
    subscriptionsField.setAccessible(true)
    val subscriptions = subscriptionsField.get(Kamon.metrics).asInstanceOf[LazyActorRef]

    subscriptions.tell(SubscriptionsDispatcher.Tick)
  }
}
