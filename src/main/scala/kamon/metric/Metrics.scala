package kamon.metric

import java.util.concurrent.{ConcurrentHashMap, ConcurrentSkipListSet, TimeUnit}
import com.codahale.metrics._
import akka.actor.ActorRef
import java.util.concurrent.atomic.AtomicReference
import com.codahale.metrics

trait MetricDepot {
  def include(name: String, metric: Metric): Unit
  def exclude(name: String): Unit
}



object Metrics extends MetricDepot {
  val registry: MetricRegistry = new MetricRegistry

  val consoleReporter = ConsoleReporter.forRegistry(registry).convertDurationsTo(TimeUnit.NANOSECONDS)
  val newrelicReporter = NewRelicReporter(registry)

  //newrelicReporter.start(5, TimeUnit.SECONDS)
  consoleReporter.build().start(10, TimeUnit.SECONDS)


  def include(name: String, metric: Metric) = registry.register(name, metric)

  def exclude(name: String) = {
    registry.removeMatching(new MetricFilter {
      def matches(name: String, metric: Metric): Boolean = name.startsWith(name)
    })
  }



  def deregister(fullName: String) = {
    registry.removeMatching(new MetricFilter {
      def matches(name: String, metric: Metric): Boolean = name.startsWith(fullName)
    })
  }
}

object Watched {
  case object Actor
  case object Dispatcher
}

object MetricDirectory {
  def nameForDispatcher(actorSystem: String, dispatcher: String) = s"/ActorSystem/${actorSystem}/Dispatcher/${dispatcher}/"

  def nameForMailbox(actorSystem: String, actor: String) = s"/ActorSystem/$actorSystem/Actor/$actor/Mailbox"

  def nameForActor(actorRef: ActorRef) = actorRef.path.elements.mkString("/")

  def shouldInstrument(actorSystem: String): Boolean = !actorSystem.startsWith("kamon")


  def shouldInstrumentActor(actorPath: String): Boolean = {
    !(actorPath.isEmpty || actorPath.startsWith("system"))
  }


}



case class ActorSystemMetrics(actorSystemName: String) {
  val dispatchers = new ConcurrentHashMap[String, DispatcherMetrics]

  def registerDispatcher(dispatcherName: String): Option[DispatcherMetricCollector] = {
    ???
  }

}



case class DispatcherMetricCollector(activeThreadCount: ValueDistributionCollector, poolSize: ValueDistributionCollector, queueSize: ValueDistributionCollector)




trait ValueDistributionCollector {
  def update(value: Long): Unit
  def snapshot: HistogramLike
}

trait HistogramLike {
  def median: Long
  def max: Long
  def min: Long
}

case class CodaHaleValueDistributionCollector extends ValueDistributionCollector {
  private[this] val histogram = new Histogram(new metrics.ExponentiallyDecayingReservoir())

  def median: Long = ???

  def max: Long = ???

  def min: Long = ???

  def snapshot: HistogramLike = histogram.getSnapshot

  def update(value: Long) = histogram.update(value)
}









/**
 *  Dispatcher Metrics that we care about currently with a histogram-like nature:
 *    - Work Queue Size
 *    - Total/Active Thread Count
 */



import annotation.tailrec
import java.util.concurrent.atomic.AtomicReference

object Atomic {
  def apply[T]( obj : T) = new Atomic(new AtomicReference(obj))
  implicit def toAtomic[T]( ref : AtomicReference[T]) : Atomic[T] = new Atomic(ref)
}

class Atomic[T](val atomic : AtomicReference[T]) {
  @tailrec
  final def update(f: T => T) : T = {
    val oldValue = atomic.get()
    val newValue = f(oldValue)
    if (atomic.compareAndSet(oldValue, newValue)) newValue else update(f)
  }

  def get() = atomic.get()
}