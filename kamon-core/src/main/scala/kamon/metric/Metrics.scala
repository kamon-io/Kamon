/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.metric

import java.util.concurrent.TimeUnit
import akka.actor.ActorRef
import com.codahale.metrics
import com.codahale.metrics.{ MetricFilter, Metric, ConsoleReporter, MetricRegistry }
import scala.collection.concurrent.TrieMap

object Metrics {
  val registry: MetricRegistry = new MetricRegistry

  val consoleReporter = ConsoleReporter.forRegistry(registry).convertDurationsTo(TimeUnit.NANOSECONDS)
  //consoleReporter.build().start(45, TimeUnit.SECONDS)

  //val newrelicReporter = NewRelicReporter(registry)
  //newrelicReporter.start(5, TimeUnit.SECONDS)

  def include(name: String, metric: Metric) = {
    //registry.register(name, metric)
  }

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

case class DispatcherMetricCollector(activeThreadCount: Histogram, poolSize: Histogram, queueSize: Histogram)

trait Histogram {
  def update(value: Long): Unit
  def snapshot: HistogramSnapshot
}

trait HistogramSnapshot {
  def median: Double
  def max: Double
  def min: Double
}

case class ActorSystemMetrics(actorSystemName: String) {
  val dispatchers = TrieMap.empty[String, DispatcherMetricCollector]

  private[this] def createDispatcherCollector: DispatcherMetricCollector = DispatcherMetricCollector(CodahaleHistogram(), CodahaleHistogram(), CodahaleHistogram())

  def registerDispatcher(dispatcherName: String): Option[DispatcherMetricCollector] = {
    val stats = createDispatcherCollector
    dispatchers.put(dispatcherName, stats)
    Some(stats)
  }

}

case class CodahaleHistogram() extends Histogram {
  private[this] val histogram = new com.codahale.metrics.Histogram(new metrics.ExponentiallyDecayingReservoir())

  def update(value: Long) = histogram.update(value)
  def snapshot: HistogramSnapshot = {
    val snapshot = histogram.getSnapshot

    CodahaleHistogramSnapshot(snapshot.getMedian, snapshot.getMax, snapshot.getMin)
  }
}

case class CodahaleHistogramSnapshot(median: Double, max: Double, min: Double) extends HistogramSnapshot

/**
 *  Dispatcher Metrics that we care about currently with a histogram-like nature:
 *    - Work Queue Size
 *    - Total/Active Thread Count
 */

import annotation.tailrec
import java.util.concurrent.atomic.AtomicReference

object Atomic {
  def apply[T](obj: T) = new Atomic(new AtomicReference(obj))
  implicit def toAtomic[T](ref: AtomicReference[T]): Atomic[T] = new Atomic(ref)
}

class Atomic[T](val atomic: AtomicReference[T]) {
  @tailrec
  final def update(f: T ⇒ T): T = {
    val oldValue = atomic.get()
    val newValue = f(oldValue)
    if (atomic.compareAndSet(oldValue, newValue)) newValue else update(f)
  }

  def get() = atomic.get()
}