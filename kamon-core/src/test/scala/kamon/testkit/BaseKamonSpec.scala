package kamon.testkit

import akka.testkit.{ ImplicitSender, TestKitBase }
import akka.actor.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory }
import kamon.Kamon
import kamon.metric.{ SubscriptionsDispatcher, EntitySnapshot, MetricsExtensionImpl }
import kamon.trace.TraceContext
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

abstract class BaseKamonSpec(actorSystemName: String) extends TestKitBase with WordSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {
  lazy val kamon = Kamon(actorSystemName, config)
  lazy val collectionContext = kamon.metrics.buildDefaultCollectionContext
  implicit lazy val system: ActorSystem = kamon.actorSystem

  def config: Config =
    ConfigFactory.load()

  def newContext(name: String): TraceContext =
    kamon.tracer.newContext(name)

  def newContext(name: String, token: String): TraceContext =
    kamon.tracer.newContext(name, token)

  def takeSnapshotOf(name: String, category: String): EntitySnapshot = {
    val recorder = kamon.metrics.find(name, category).get
    recorder.collect(collectionContext)
  }

  def flushSubscriptions(): Unit =
    system.actorSelection("/user/kamon/subscriptions-dispatcher") ! SubscriptionsDispatcher.Tick

  override protected def afterAll(): Unit = system.shutdown()
}
