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

package kamon.jmx

import java.lang.management.ManagementFactory
import javax.management._

import akka.actor.{ Actor, Props }
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{ Counter, Histogram, InstrumentSnapshot }
import kamon.util.logger.LazyLogger

import scala.collection.concurrent.TrieMap

private object MetricMBeans {

  private implicit class RichHistogramSnapshot(histogram: Histogram.Snapshot) {
    def average: Double = {
      if (histogram.numberOfMeasurements == 0) 0D
      else histogram.sum / histogram.numberOfMeasurements
    }
  }

  private[jmx] sealed trait MetricMBean

  private[jmx] abstract class AbstractMetricMBean[T <: InstrumentSnapshot] extends MetricMBean {
    private[jmx] var snapshot: T

    private[jmx] def objectName: ObjectName
  }

  private[jmx] trait HistogramMetricMBean extends AbstractMetricMBean[Histogram.Snapshot] {
    def getNumberOfMeasurements: Long

    def getMin: Long

    def get50thPercentile: Long

    def getPercentile(n: Double): Long

    def get70thPercentile: Long

    def get90thPercentile: Long

    def get95thPercentile: Long

    def get990thPercentile: Long

    def get999thPercentile: Long

    def getAvg: Double

    def getMax: Long

    def getSum: Long
  }

  private[jmx] class HistogramMetric(@volatile private[jmx] override var snapshot: Histogram.Snapshot, private[jmx] override val objectName: ObjectName) extends HistogramMetricMBean {
    override def getNumberOfMeasurements: Long = snapshot.numberOfMeasurements

    override def getMin: Long = snapshot.min

    override def get50thPercentile: Long = snapshot.percentile(50.0D)

    override def get70thPercentile: Long = snapshot.percentile(70.0D)

    override def get90thPercentile: Long = snapshot.percentile(90.0D)

    override def get95thPercentile: Long = snapshot.percentile(95.0D)

    override def get990thPercentile: Long = snapshot.percentile(99.0D)

    override def get999thPercentile: Long = snapshot.percentile(99.9D)

    override def getPercentile(n: Double): Long = snapshot.percentile(n)

    override def getMax: Long = snapshot.max

    override def getSum: Long = snapshot.sum

    override def getAvg: Double = snapshot.average
  }

  private[jmx] trait CounterMetricMBean extends AbstractMetricMBean[Counter.Snapshot] {
    def getCount: Long
  }

  private[jmx] trait HistogramValueMetricMBean extends AbstractMetricMBean[Histogram.Snapshot] {
    def getValue: Long
  }

  private[jmx] class CounterMetric(@volatile private[jmx] override var snapshot: Counter.Snapshot, private[jmx] override val objectName: ObjectName) extends CounterMetricMBean {
    override def getCount: Long = snapshot.count
  }

  private[jmx] class HistogramValueMetric(@volatile private[jmx] override var snapshot: Histogram.Snapshot, private[jmx] override val objectName: ObjectName) extends HistogramValueMetricMBean {
    override def getValue: Long = snapshot.max
  }

  private[jmx] implicit def impCreateHistogramMetric(snapshot: Histogram.Snapshot, objectName: ObjectName) = new HistogramMetric(snapshot, objectName)

  private[jmx] implicit def impCounterMetric(snapshot: Counter.Snapshot, objectName: ObjectName) = new CounterMetric(snapshot, objectName)

}

private object MBeanManager {

  import MetricMBeans._

  private val mbs = ManagementFactory.getPlatformMBeanServer

  private val registeredMBeans = TrieMap.empty[String, AbstractMetricMBean[_]]

  private val log = LazyLogger(getClass)

  private[jmx] def createOrUpdateMBean[M <: AbstractMetricMBean[T], T <: InstrumentSnapshot](group: String, name: String, snapshot: T)(implicit buildMetricMBean: (T, ObjectName) ⇒ M): Unit = {
    registeredMBeans.get(name) match {
      case Some(mbean: M) ⇒
        mbean.snapshot = snapshot
      case None ⇒
        val objectName = new ObjectName(createMBeanName("kamon", group, name))
        val mbean = buildMetricMBean(snapshot, objectName)
        registeredMBeans += name -> mbean
        mbs.registerMBean(mbean, objectName)
      case _ ⇒ throw new IllegalStateException("Illegal metric bean type")
    }
  }

  private[jmx] def unregisterAllBeans(): Unit = {
    registeredMBeans.values.map(_.objectName).foreach(name ⇒
      try {
        mbs.unregisterMBean(name)
      } catch {
        case e: InstanceNotFoundException  ⇒ if (log.isTraceEnabled) log.trace(s"Error unregistering $name", e)
        case e: MBeanRegistrationException ⇒ if (log.isDebugEnabled) log.debug(s"Error unregistering $name", e)
      })
    registeredMBeans.clear()
  }

  private def createMBeanName(group: String, `type`: String, name: String, scope: Option[String] = None): String = {
    val nameBuilder: StringBuilder = new StringBuilder
    nameBuilder.append(group)
    nameBuilder.append(":type=")
    nameBuilder.append(`type`)
    if (scope.isDefined) {
      nameBuilder.append(",scope=")
      nameBuilder.append(scope.get)
    }
    if (name.length > 0) {
      nameBuilder.append(",name=")
      nameBuilder.append(name)
    }
    nameBuilder.toString
  }
}

private object JMXReporterActor {

  import MBeanManager._
  import MetricMBeans._

  private[jmx] def props = Props(classOf[JMXReporterActor])

  private def updateHystogramMetrics(group: String, name: String, hs: Histogram.Snapshot): Unit = {
    createOrUpdateMBean[HistogramMetric, Histogram.Snapshot](group, name, hs)
  }

  private def updateCounterMetrics(group: String, name: String, cs: Counter.Snapshot): Unit = {
    createOrUpdateMBean[CounterMetric, Counter.Snapshot](group, name, cs)
  }
}

private class JMXReporterActor extends Actor {

  import JMXReporterActor._
  import MBeanManager.unregisterAllBeans

  def receive: Receive = {
    case tick: TickMetricSnapshot ⇒
      for {
        (entity, snapshot) ← tick.metrics
        (metricKey, metricSnapshot) ← snapshot.metrics
      } {
        metricSnapshot match {
          case hs: Histogram.Snapshot ⇒
            updateHystogramMetrics(entity.category, entity.name + "." + metricKey.name, hs)
          case cs: Counter.Snapshot ⇒
            updateCounterMetrics(entity.category, entity.name + "." + metricKey.name, cs)
        }
      }
  }

  override def postStop(): Unit = {
    super.postStop()
    unregisterAllBeans()
  }
}