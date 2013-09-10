package kamon

import akka.actor.{Actor, Props, ActorSystem}
import kamon.metric.{HistogramSnapshot, ActorSystemMetrics}
import scala.concurrent.duration.FiniteDuration
import com.newrelic.api.agent.NewRelic
import scala.collection.concurrent.TrieMap
import kamon.instrumentation.{SimpleContextPassingInstrumentation, ActorInstrumentationConfiguration}


object Instrument {
  val instrumentation: ActorInstrumentationConfiguration = new SimpleContextPassingInstrumentation
}

object Kamon {
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


object Tracer {
  val ctx = new ThreadLocal[Option[TraceContext]] {
    override def initialValue() = None
  }

  def context() = ctx.get()
  def clear = ctx.remove()
  def set(traceContext: TraceContext) = ctx.set(Some(traceContext))

  def start = set(newTraceContext)
  def stop = ctx.get match {
    case Some(context) => context.close
    case None =>
  }

  def newTraceContext(): TraceContext = TraceContext()(Kamon.actorSystem)
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






class NewrelicReporterActor extends Actor {
  import scala.concurrent.duration._

  //Kamon.metricManager ! RegisterForAllDispatchers(5 seconds)

  def receive = {
    case DispatcherMetrics(actorSystem, dispatcher, activeThreads, poolSize, queueSize) => {
      /*println("PUBLISHED DISPATCHER STATS")
      println(s"Custom/$actorSystem/Dispatcher/$dispatcher/Threads/active =>" + activeThreads.median.toFloat)
      println(s"Custom/$actorSystem/Dispatcher/$dispatcher/Threads/inactive =>" + (poolSize.median.toFloat-activeThreads.median.toFloat))
      println(s"Custom/$actorSystem/Dispatcher/$dispatcher/Queue =>" + queueSize.median.toFloat)*/


      NewRelic.recordMetric(s"Custom/$actorSystem/Dispatcher/$dispatcher/Threads/active", activeThreads.median.toFloat)
      NewRelic.recordMetric(s"Custom/$actorSystem/Dispatcher/$dispatcher/Threads/inactive", (poolSize.median.toFloat-activeThreads.median.toFloat))

      NewRelic.recordMetric(s"Custom/$actorSystem/Dispatcher/$dispatcher/Queue", queueSize.median.toFloat)
    }
  }
}