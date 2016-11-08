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

package kamon.jmx.extension

import java.lang.Thread
import java.lang.management.ManagementFactory
import java.util.concurrent.{ Executors, ScheduledExecutorService, TimeUnit }
import java.util.logging.Level

import scala.concurrent.ExecutionContext
import scala.collection.mutable.Buffer
import scala.collection.immutable.Set
import scala.concurrent.duration.FiniteDuration

import akka.actor.{ Actor, ActorSystem, ExtendedActorSystem, Props }
import akka.event.Logging

import javax.management._

import com.typesafe.config.{ Config, ConfigValueType }

import kamon.metric.{
  GenericEntityRecorder,
  MetricsModule,
  EntityRecorderFactory
}
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument._
import kamon.metric.instrument.Gauge.CurrentValueCollector
import kamon.jmx.extension.MetricDefinition._

class AcceptAllPredicate extends QueryExp {

  def apply(name: ObjectName): Boolean = true

  def setMBeanServer(s: MBeanServer): Unit = {}
}

object ExportedMBeanQuery {

  val server: MBeanServer = ManagementFactory.getPlatformMBeanServer()
  val pred: QueryExp = new AcceptAllPredicate()

  /**
   * register queries to find new dynamic mbeans
   * @param system actor system used by kamon
   * @param metricsExtension kamon object for registering metrics
   * @param config configuration of this kamon extension
   */
  def register(
    system: ExtendedActorSystem, metricsExtension: MetricsModule,
    config: Config): Unit = {

    println("registering jmx exporter")
    Thread.currentThread().setName("JMX exporting thread")
    import collection.JavaConverters._
    val identifyDelayInterval: Long =
      config.getLong("identify-delay-interval-ms")
    val identifyInterval: Long = config.getLong("identify-interval-ms")
    val checkInterval: Long = config.getLong("value-check-interval-ms")

    config.getObjectList("mbeans").asScala.foreach { confObj ⇒
      val nameObj = confObj.get("name")
      val queryObj = confObj.get("jmxQuery")
      val attrListObj = confObj.get("attributes")

      require(
        nameObj.valueType() == ConfigValueType.STRING, "name must be a string")
      require(
        queryObj.valueType() == ConfigValueType.STRING,
        "jmxQuery must be a string")
      require(
        attrListObj.valueType() == ConfigValueType.LIST,
        "mbeans must be an array")

      val name: String = nameObj.unwrapped().asInstanceOf[String]
      val query: String = queryObj.unwrapped().asInstanceOf[String]
      val attrList: Seq[Map[String, Any]] =
        attrListObj.unwrapped().asInstanceOf[java.util.List[Any]].asScala.map {
          import scala.collection.JavaConversions._
          attr ⇒ attr.asInstanceOf[java.util.HashMap[String, Any]].toMap
        }

      val jmxQuery: ObjectName = new ObjectName(query)
      if (metricsExtension.shouldTrack(name, "kamon-mxbeans")) {

        val subscriber = system.actorOf(
          Props(classOf[ExportedMBeanQuery], system, jmxQuery, attrList,
            identifyDelayInterval, identifyInterval, checkInterval),
          name)
      }
    }
  }

  /**
   * @param system actor system used by kamon
   * @param jmxQuery query to lookup dynamic mbeans from the JVM
   * @param attributeConfigs configuration for each attribute
   * @param identifyDelayInterval how long to wait to look for mbeans the
   * first time
   * @param identifyInterval how often to look for new mbeans
   * @param checkInterval how often to check for new values for metrics
   */
  def apply(
    system: ExtendedActorSystem,
    jmxQuery: ObjectName, attributeConfigs: Seq[Map[String, Any]],
    identifyDelayInterval: Long, identifyInterval: Long,
    checkInterval: Long): ExportedMBeanQuery =
    new ExportedMBeanQuery(
      system, jmxQuery, attributeConfigs,
      identifyDelayInterval, identifyInterval, checkInterval)
}

/**
 * Uses a jmx query to find dynamic mbeans and registers them to be listened
 * to by kamon.  This is the object that actually moves metrics from JMX
 * into Kamon.
 *
 * @param system actor system used by kamon
 * @param jmxQuery query to use to look for new dynamic mbeans
 * @param attributeConfig configuration of each metric for this kind of
 * mbean
 * @param identifyDelayInterval how long to wait to look for mbeans the
 * first time
 * @param identifyInterval how often to look for new mbeans
 * @param checkInterval how often to check for new values for metrics
 */
class ExportedMBeanQuery(
    val system: ExtendedActorSystem,
    val jmxQuery: ObjectName, val attributeConfigs: Seq[Map[String, Any]],
    val identifyDelayInterval: Long, val identifyInterval: Long,
    val checkInterval: Long) extends Actor {

  import context.dispatcher // ExecutionContext for the futures and scheduler
  val log = Logging(system, getClass)

  require(
    identifyInterval >= checkInterval,
    "not checking JMX values often enough: " +
      identifyInterval + " < " + checkInterval)
  import ExportedMBeanQuery.{ server, pred }
  import ExportedMBean.{ apply }

  val monitoredBeanNames: collection.mutable.Set[ObjectName] =
    collection.mutable.Set[ObjectName]()
  var lastCheck: Long = 0

  val attributeNames: Array[String] = attributeConfigs.map { m ⇒
    m("name").asInstanceOf[String]
  }.toArray
  val configMap: Map[String, Map[String, Any]] = attributeConfigs.map { conf ⇒
    (conf("name").asInstanceOf[String], conf)
  }.toMap

  // queries the current set of dynamic mbeans and returns their identifing
  // names
  def getMBeanNames(): Set[ObjectName] = {
    import collection.JavaConverters._
    server.queryNames(jmxQuery, pred).asScala.toSet
  }

  /**
   * looks for new dynamic mbeans and registers an object to watch those
   * new mbeans for changes in their metric values and send them into kamon
   * metrics
   */
  def rebuildRecorders(): Unit = {

    import collection.JavaConverters._
    val names: Set[ObjectName] = getMBeanNames()
    log.debug("found JMX ObjectNames " + names)
    names.filterNot { name ⇒
      monitoredBeanNames.exists(_.equals(name))
    }.foreach { name ⇒
      val attrList: AttributeList =
        server.getAttributes(name, attributeNames)
      val attrs: Seq[Attribute] = attrList.asList().asScala
      val values: Map[String, Attribute] =
        attrs.map { attr ⇒ (attr.getName(), attr) }.toMap

      val definitions: Seq[MetricDefinition] = values.map {
        case (name, attr) ⇒
          val config: Map[String, Any] = configMap(name)
          if ("gauge".equalsIgnoreCase(
            config("type").asInstanceOf[String])) {
            new MetricDefinition(
              config, None, Some(new CurrentValueCollector {
                def currentValue: Long = ExportedMBean.toLong(attr.getValue())
              }))
          } else {
            new MetricDefinition(config, None, None)
          }
      }.toSeq

      val metricsExtension = kamon.Kamon.metrics
      metricsExtension.entity(
        EntityRecorderFactory(
          "kamon-mxbeans",
          apply(
            system, _, definitions, name, attributeNames, checkInterval)),
        name.getKeyProperty("name"))

      monitoredBeanNames += name
    }
  }

  def receive: PartialFunction[Any, Unit] = {
    case tick: TickMetricSnapshot ⇒ rebuildRecorders()
  }

  system.scheduler.schedule(
    FiniteDuration(identifyDelayInterval, TimeUnit.MILLISECONDS),
    FiniteDuration(identifyInterval, TimeUnit.MILLISECONDS),
    new Runnable() {
      def run(): Unit = rebuildRecorders()
    })
}

/**
 * An object that manages a specific dynamic mbean for kamon.
 * @param system actor system used by kamon
 * @param instrumentFactory kamon facade for making metrics
 * @param definitions representations of each kamon metric in this mbean
 * @param objName name of the JMX object to monitor
 * @param attrNames jmx attributes to query for metric values
 * @param checkInterval how often to check for new metric values from this
 *  mbean
 */
class ExportedMBean(
  val system: ExtendedActorSystem,
  val instrumentFactory: InstrumentFactory,
  val definitions: Seq[MetricDefinition], val objName: ObjectName,
  val attrNames: Array[String], val checkInterval: Long)(
    implicit ec: ExecutionContext)
    extends GenericEntityRecorder(instrumentFactory) {

  import ExportedMBean._

  val log = Logging(system, getClass)
  import kamon.jmx.extension.ExportedMBeanQuery.server

  // metrics we created for this mbean
  val counters: Map[String, Counter] =
    definitions.filter(_.metricType == MetricTypeEnum.COUNTER).map(mdef ⇒
      (mdef.name, makeCounter(mdef))).toMap
  val histograms: Map[String, Histogram] =
    definitions.filter(_.metricType == MetricTypeEnum.HISTOGRAM).map(mdef ⇒
      (mdef.name, makeHistogram(mdef))).toMap
  val minMaxCounters: Map[String, MinMaxCounter] =
    definitions.filter(_.metricType == MetricTypeEnum.MIN_MAX_COUNTER).map(
      mdef ⇒ (mdef.name, makeMinMaxCounter(mdef))).toMap
  val gauges: Map[String, Gauge] =
    definitions.filter(_.metricType == MetricTypeEnum.GAUGE).map(
      mdef ⇒ (mdef.name, makeGauge(mdef))).toMap

  def gatherMetrics(): Unit = {
    import collection.JavaConverters._

    try {
      val attrList: AttributeList = server.getAttributes(objName, attrNames)
      attrList.asList().asScala.foreach { attr ⇒
        val attrName: String = attr.getName()
        if (counters.contains(attrName)) {
          counters(attrName).increment(toLong(attr.getValue()))
        } else if (histograms.contains(attrName)) {
          histogram(attrName).record(toLong(attr.getValue()))
        } else if (minMaxCounters.contains(attrName)) {
          val value: Long = toLong(attr.getValue())
          minMaxCounters(attrName).increment(value)
        }
      }
    } catch {
      case e: javax.management.JMException ⇒ log.error(e, e.getMessage())
      case e1: Throwable                   ⇒ log.error(e1, e1.getMessage())
    }
  }

  protected def makeCounter(mdef: MetricDefinition): Counter = {
    require(mdef.metricType == MetricTypeEnum.COUNTER)
    require(mdef.name != null, "a metric should have a name")
    require(!mdef.range.isDefined, "a counter can't define a range")
    require(
      !mdef.refreshInterval.isDefined,
      "a counter can't define a refresh interval")
    require(
      !mdef.valueCollector.isDefined,
      "a counter can't define a value collector")
    counter(mdef.name, mdef.unitOfMeasure)
  }

  protected def makeHistogram(mdef: MetricDefinition): Histogram = {
    require(mdef.metricType == MetricTypeEnum.HISTOGRAM)
    require(mdef.name != null, "a histogram should have a name")
    require(
      !mdef.refreshInterval.isDefined,
      "a histogram can't have a refresh interval")
    require(
      !mdef.valueCollector.isDefined,
      "a histogram can't have a value collector")
    if (!mdef.range.isDefined) {
      histogram(mdef.name, mdef.unitOfMeasure)
    } else {
      histogram(mdef.name, mdef.range.get, mdef.unitOfMeasure)
    }
  }

  protected def makeMinMaxCounter(mdef: MetricDefinition): MinMaxCounter = {
    require(mdef.metricType == MetricTypeEnum.MIN_MAX_COUNTER)
    require(mdef.name != null, "a min-max counter must have a name")
    require(
      !mdef.valueCollector.isDefined,
      "a min-max counter can't define a value collector")
    if (!mdef.range.isDefined && !mdef.refreshInterval.isDefined) {
      minMaxCounter(mdef.name, mdef.unitOfMeasure)
    } else if (!mdef.refreshInterval.isDefined) {
      minMaxCounter(mdef.name, mdef.range.get, mdef.unitOfMeasure)
    } else if (!mdef.range.isDefined) {
      minMaxCounter(mdef.name, mdef.refreshInterval.get, mdef.unitOfMeasure)
    } else {
      minMaxCounter(
        mdef.name, mdef.range.get, mdef.refreshInterval.get, mdef.unitOfMeasure)
    }
  }

  protected def makeGauge(mdef: MetricDefinition): Gauge = {
    require(mdef.metricType == MetricTypeEnum.GAUGE)
    require(mdef.name != null, "a gauge must have a name")
    require(
      mdef.valueCollector.isDefined, "a gauge must have a value collector")
    if (mdef.range.isDefined && !mdef.refreshInterval.isDefined) {
      gauge(mdef.name, mdef.unitOfMeasure, mdef.valueCollector.get)
    } else if (!mdef.range.isDefined) {
      gauge(
        mdef.name, mdef.refreshInterval.get, mdef.unitOfMeasure,
        mdef.valueCollector.get)
    } else if (!mdef.refreshInterval.isDefined) {
      gauge(
        mdef.name, mdef.range.get, mdef.unitOfMeasure, mdef.valueCollector.get)
    } else {
      gauge(
        mdef.name, mdef.range.get, mdef.refreshInterval.get, mdef.unitOfMeasure,
        mdef.valueCollector.get)
    }
  }

  system.scheduler.schedule(
    FiniteDuration(checkInterval, TimeUnit.MILLISECONDS),
    FiniteDuration(checkInterval, TimeUnit.MILLISECONDS),
    new Runnable() {
      def run(): Unit = gatherMetrics()
    })
  log.debug("scheduled reading of JMX metrics from " + objName)
}

object ExportedMBean {

  protected[extension] def toLong(v: Any): Long = v match {
    case x: Long   ⇒ x
    case n: Number ⇒ n.asInstanceOf[Number].longValue
    case _         ⇒ throw new IllegalArgumentException(s"$v is not a number.")
  }

  /**
   * @param system actor system used by kamon
   * @param instrumentFactory kamon facade for making metrics
   * @param definitions the configuration of metrics to push into kamon
   * @param objName the name of the jmx object we are monitoring
   * @param attrNames the names of the attributes we are monitoring
   * @param checkInterval how often to check for new mbeans
   */
  def apply(
    system: ExtendedActorSystem,
    instrumentFactory: InstrumentFactory,
    definitions: Seq[MetricDefinition],
    objName: ObjectName, attrNames: Array[String],
    checkInterval: Long)(implicit ec: ExecutionContext): ExportedMBean =
    new ExportedMBean(
      system, instrumentFactory, definitions, objName, attrNames,
      checkInterval)
}
