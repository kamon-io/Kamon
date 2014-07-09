package kamon.standalone

import akka.actor.ActorSystem
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.UserMetrics
import kamon.metric.instrument.{ Gauge, MinMaxCounter, Counter, Histogram }

import scala.concurrent.duration.FiniteDuration

trait KamonStandalone {
  private[kamon] def system: ActorSystem

  def registerHistogram(name: String, precision: Histogram.Precision, highestTrackableValue: Long): Histogram =
    Kamon(UserMetrics)(system).registerHistogram(name, precision, highestTrackableValue)

  def registerHistogram(name: String): Histogram =
    Kamon(UserMetrics)(system).registerHistogram(name)

  def registerCounter(name: String): Counter =
    Kamon(UserMetrics)(system).registerCounter(name)

  def registerMinMaxCounter(name: String, precision: Histogram.Precision, highestTrackableValue: Long,
    refreshInterval: FiniteDuration): MinMaxCounter =
    Kamon(UserMetrics)(system).registerMinMaxCounter(name, precision, highestTrackableValue, refreshInterval)

  def registerMinMaxCounter(name: String): MinMaxCounter =
    Kamon(UserMetrics)(system).registerMinMaxCounter(name)

  def registerGauge(name: String)(currentValueCollector: Gauge.CurrentValueCollector): Gauge =
    Kamon(UserMetrics)(system).registerGauge(name)(currentValueCollector)

  def registerGauge(name: String, precision: Histogram.Precision, highestTrackableValue: Long,
    refreshInterval: FiniteDuration)(currentValueCollector: Gauge.CurrentValueCollector): Gauge =
    Kamon(UserMetrics)(system).registerGauge(name, precision, highestTrackableValue, refreshInterval)(currentValueCollector)

  def removeHistogram(name: String): Unit =
    Kamon(UserMetrics)(system).removeHistogram(name)

  def removeCounter(name: String): Unit =
    Kamon(UserMetrics)(system).removeCounter(name)

  def removeMinMaxCounter(name: String): Unit =
    Kamon(UserMetrics)(system).removeMinMaxCounter(name)

  def removeGauge(name: String): Unit =
    Kamon(UserMetrics)(system).removeGauge(name)
}

object KamonStandalone {

  def buildFromConfig(config: Config): KamonStandalone = buildFromConfig(config, "kamon-standalone")

  def buildFromConfig(config: Config, actorSystemName: String): KamonStandalone = new KamonStandalone {
    val system: ActorSystem = ActorSystem(actorSystemName, config)
  }
}

object EmbeddedKamonStandalone extends KamonStandalone {
  private[kamon] lazy val system = ActorSystem("kamon-standalone")
}