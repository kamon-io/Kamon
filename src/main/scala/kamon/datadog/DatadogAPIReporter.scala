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
import kamon.metric.{ MeasurementUnit, MetricSnapshot, PeriodSnapshot }
import kamon.tag.TagSet
import kamon.util.{ EnvironmentTags, Filter }
import kamon.Kamon
import kamon.module.MetricReporter
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

  logger.info("Started the Datadog API reporter.")

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
        logger.trace(response)
    }
  }

  private[datadog] def buildRequestBody(snapshot: PeriodSnapshot): Array[Byte] = {
    val timestamp = snapshot.from.getEpochSecond.toString

    val host = Kamon.environment.host
    val interval = Math.round(Duration.between(snapshot.from, snapshot.to).toMillis() / 1000D)
    val seriesBuilder = new StringBuilder()

    def addDistribution(metric: MetricSnapshot.Distributions): Unit = {
      val unit = metric.settings.unit
      metric.instruments.foreach { d =>
        val dist = d.value

        val average = if (dist.count > 0L) (dist.sum / dist.count) else 0L
        addMetric(metric.name + ".avg", valueFormat.format(scale(average, unit)), gauge, d.tags)
        addMetric(metric.name + ".count", valueFormat.format(dist.count), count, d.tags)
        addMetric(metric.name + ".median", valueFormat.format(scale(dist.percentile(50D).value, unit)), gauge, d.tags)
        addMetric(metric.name + ".95percentile", valueFormat.format(scale(dist.percentile(95D).value, unit)), gauge, d.tags)
        addMetric(metric.name + ".max", valueFormat.format(scale(dist.max, unit)), gauge, d.tags)
        addMetric(metric.name + ".min", valueFormat.format(scale(dist.min, unit)), gauge, d.tags)
      }
    }

    def addMetric(metricName: String, value: String, metricType: String, tags: TagSet): Unit = {
      val customTags = (configuration.extraTags ++ tags.iterator(_.toString).map(p => p.key -> p.value).filter(t => configuration.tagFilter.accept(t._1))).map { case (k, v) ⇒ quote"$k:$v" }
      val allTagsString = customTags.mkString("[", ",", "]")

      if (seriesBuilder.length() > 0) seriesBuilder.append(",")

      seriesBuilder
        .append(s"""{"metric":"$metricName","interval":$interval,"points":[[$timestamp,$value]],"type":"$metricType","host":"$host","tags":$allTagsString}""")
    }

    snapshot.counters.foreach { snap =>
      snap.instruments.foreach { instrument =>
        addMetric(
          snap.name,
          valueFormat.format(scale(instrument.value, snap.settings.unit)),
          count,
          instrument.tags
        )
      }
    }
    snapshot.gauges.foreach { snap =>
      snap.instruments.foreach { instrument =>
        addMetric(
          snap.name,
          valueFormat.format(scale(instrument.value, snap.settings.unit)),
          gauge,
          instrument.tags
        )
      }
    }

    (snapshot.histograms ++ snapshot.rangeSamplers).foreach(addDistribution)

    seriesBuilder
      .insert(0, "{\"series\":[")
      .append("]}")
      .toString()
      .getBytes(StandardCharsets.UTF_8)

  }

  private def scale(value: Double, unit: MeasurementUnit): Double = unit.dimension match {
    case Time if unit.magnitude != configuration.timeUnit.magnitude =>
      MeasurementUnit.convert(value, unit, configuration.timeUnit)

    case Information if unit.magnitude != configuration.informationUnit.magnitude =>
      MeasurementUnit.convert(value, unit, configuration.informationUnit)

    case _ => value
  }

  private def readConfiguration(config: Config): Configuration = {
    val datadogConfig = config.getConfig("kamon.datadog")
    Configuration(
      datadogConfig.getConfig("http"),
      timeUnit = readTimeUnit(datadogConfig.getString("time-unit")),
      informationUnit = readInformationUnit(datadogConfig.getString("information-unit")),
      // Remove the "host" tag since it gets added to the datadog payload separately
      EnvironmentTags.from(Kamon.environment, datadogConfig.getConfig("additional-tags")).iterator(_.toString).filterNot(_.key == "host").map(p => p.key -> p.value).toSeq,
      Kamon.filter(datadogConfig.getString("filter-config-key"))
    )
  }
}

private object DatadogAPIReporter {
  val count = "count"
  val gauge = "gauge"

  case class Configuration(httpConfig: Config, timeUnit: MeasurementUnit, informationUnit: MeasurementUnit, extraTags: Seq[(String, String)], tagFilter: Filter)

  implicit class QuoteInterp(val sc: StringContext) extends AnyVal {
    def quote(args: Any*): String = "\"" + sc.s(args: _*) + "\""
  }
}
