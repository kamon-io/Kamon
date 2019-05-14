/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.datadog

import java.lang.StringBuilder
import java.nio.charset.StandardCharsets
import java.text.{ DecimalFormat, DecimalFormatSymbols }
import java.time.Duration
import java.util.Locale

import com.typesafe.config.Config
import kamon.metric.MeasurementUnit.Dimension.{ Information, Time }
import kamon.metric.{ MeasurementUnit, MetricDistribution, MetricValue, PeriodSnapshot }
import kamon.util.{ EnvironmentTagBuilder, Matcher }
import kamon.{ Kamon, MetricReporter }
import org.slf4j.LoggerFactory

import scala.util.{ Failure, Success }

class DatadogAPIReporter extends MetricReporter {
  import DatadogAPIReporter._

  private val logger = LoggerFactory.getLogger(classOf[DatadogAPIReporter])
  private val symbols = DecimalFormatSymbols.getInstance(Locale.US)
  symbols.setDecimalSeparator('.') // Just in case there is some weird locale config we are not aware of.

  private val valueFormat = new DecimalFormat("#0.#########", symbols)
  private var configuration = readConfiguration(Kamon.config())
  private var httpClient: HttpClient = new HttpClient(configuration.httpConfig)

  override def start(): Unit = {
    logger.info("Started the Datadog API reporter.")
  }

  override def stop(): Unit = {
    logger.info("Stopped the Datadog API reporter.")
  }

  override def reconfigure(config: Config): Unit = {
    val newConfiguration = readConfiguration(config)
    configuration = newConfiguration
    httpClient = new HttpClient(configuration.httpConfig)
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    httpClient.doPost("application/json; charset=utf-8", buildRequestBody(snapshot)) match {
      case Failure(e) =>
        logger.error(e.getMessage)
      case Success(response) =>
        logger.info(response)
    }
  }

  private[datadog] def buildRequestBody(snapshot: PeriodSnapshot): Array[Byte] = {
    val timestamp = snapshot.from.getEpochSecond.toString

    val host = Kamon.environment.host
    val interval = Math.round(Duration.between(snapshot.from, snapshot.to).toMillis() / 1000D)
    val seriesBuilder = new StringBuilder()

    def addDistribution(metric: MetricDistribution): Unit = {
      import metric._

      val average = if (distribution.count > 0L) (distribution.sum / distribution.count) else 0L
      addMetric(name + ".avg", valueFormat.format(scale(average, unit)), gauge, metric.tags)
      addMetric(name + ".count", valueFormat.format(distribution.count), count, metric.tags)
      addMetric(name + ".median", valueFormat.format(scale(distribution.percentile(50D).value, unit)), gauge, metric.tags)
      addMetric(name + ".95percentile", valueFormat.format(scale(distribution.percentile(95D).value, unit)), gauge, metric.tags)
      addMetric(name + ".max", valueFormat.format(scale(distribution.max, unit)), gauge, metric.tags)
      addMetric(name + ".min", valueFormat.format(scale(distribution.min, unit)), gauge, metric.tags)
    }

    def addMetric(metricName: String, value: String, metricType: String, tags: Map[String, String]): Unit = {
      val customTags = (configuration.extraTags ++ tags.filterKeys(configuration.tagFilter.accept)).map { case (k, v) ⇒ quote"$k:$v" }.toSeq
      val allTagsString = customTags.mkString("[", ",", "]")

      if (seriesBuilder.length() > 0) seriesBuilder.append(",")

      seriesBuilder
        .append(s"""{"metric":"$metricName","interval":$interval,"points":[[$timestamp,$value]],"type":"$metricType","host":"$host","tags":$allTagsString}""")
    }

    def add(metric: MetricValue, metricType: String): Unit =
      addMetric(metric.name, valueFormat.format(scale(metric.value, metric.unit)), metricType, metric.tags)

    snapshot.metrics.counters.foreach(add(_, count))
    snapshot.metrics.gauges.foreach(add(_, gauge))

    (snapshot.metrics.histograms ++ snapshot.metrics.rangeSamplers).foreach(addDistribution)

    seriesBuilder
      .insert(0, "{\"series\":[")
      .append("]}")
      .toString()
      .getBytes(StandardCharsets.UTF_8)

  }

  private def scale(value: Long, unit: MeasurementUnit): Double = unit.dimension match {
    case Time if unit.magnitude != configuration.timeUnit.magnitude =>
      MeasurementUnit.scale(value, unit, configuration.timeUnit)

    case Information if unit.magnitude != configuration.informationUnit.magnitude =>
      MeasurementUnit.scale(value, unit, configuration.informationUnit)

    case _ => value.toDouble
  }

  private def readConfiguration(config: Config): Configuration = {
    val datadogConfig = config.getConfig("kamon.datadog")
    Configuration(
      datadogConfig.getConfig("http"),
      timeUnit = readTimeUnit(datadogConfig.getString("time-unit")),
      informationUnit = readInformationUnit(datadogConfig.getString("information-unit")),
      // Remove the "host" tag since it gets added to the datadog payload separately
      EnvironmentTagBuilder.create(datadogConfig.getConfig("additional-tags")) - "host",
      Kamon.filter(datadogConfig.getString("filter-config-key"))
    )
  }
}

private object DatadogAPIReporter {
  val count = "count"
  val gauge = "gauge"

  case class Configuration(httpConfig: Config, timeUnit: MeasurementUnit, informationUnit: MeasurementUnit, extraTags: Map[String, String], tagFilter: Matcher)

  implicit class QuoteInterp(val sc: StringContext) extends AnyVal {
    def quote(args: Any*): String = "\"" + sc.s(args: _*) + "\""
  }
}
