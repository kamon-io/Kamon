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

  object Metric {

    val actorSystems =  TrieMap.empty[String, ActorSystemMetrics]

    def actorSystemNames: List[String] = actorSystems.keys.toList
    def registerActorSystem(name: String) = actorSystems.getOrElseUpdate(name, ActorSystemMetrics(name))

    def actorSystem(name: String): Option[ActorSystemMetrics] = actorSystems.get(name)
  }

  //val metricManager = actorSystem.actorOf(Props[MetricManager], "metric-manager")
  //val newrelicReporter = actorSystem.actorOf(Props[NewrelicReporterActor], "newrelic-reporter")

}


class MetricManager extends Actor {
  implicit val ec = context.system.dispatcher

  def receive = {
    case RegisterForAllDispatchers(frequency) => {
      val subscriber = sender
      context.system.scheduler.schedule(frequency, frequency) {
        Kamon.Metric.actorSystems.foreach {
          case (asName, actorSystemMetrics) => actorSystemMetrics.dispatchers.foreach {
            case (dispatcherName, dispatcherMetrics) => {
              val activeThreads = dispatcherMetrics.activeThreadCount.snapshot
              val poolSize = dispatcherMetrics.poolSize.snapshot
              val queueSize = dispatcherMetrics.queueSize.snapshot

              subscriber ! DispatcherMetrics(asName, dispatcherName, activeThreads, poolSize, queueSize)

            }
          }
        }
      }
    }
  }
}

case class RegisterForAllDispatchers(frequency: FiniteDuration)
case class DispatcherMetrics(actorSystem: String, dispatcher: String, activeThreads: HistogramSnapshot, poolSize: HistogramSnapshot, queueSize: HistogramSnapshot)
