package kamon

import akka.actor._
import kamon.metric.{HistogramSnapshot, ActorSystemMetrics}
import scala.concurrent.duration.FiniteDuration
import scala.collection.concurrent.TrieMap
import kamon.instrumentation.{SimpleContextPassingInstrumentation, ActorInstrumentationConfiguration}
import kamon.metric.ActorSystemMetrics


object Instrument {
  val instrumentation: ActorInstrumentationConfiguration = new SimpleContextPassingInstrumentation
}

object Kamon {
  trait Extension extends akka.actor.Extension {
    def manager: ActorRef
  }

  def apply[T <: Extension](key: ExtensionId[T])(implicit system: ActorSystem): ActorRef = key(system).manager


  implicit lazy val actorSystem = ActorSystem("kamon")
}

