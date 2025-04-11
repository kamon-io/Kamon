/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.datadog

import java.nio.charset.StandardCharsets
import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.time.Duration
import java.util.Locale
import com.typesafe.config.Config
import kamon.metric.MeasurementUnit.Dimension.{Information, Time}
import kamon.metric.{MeasurementUnit, MetricSnapshot, PeriodSnapshot}
import kamon.tag.{Tag, TagSet}
import kamon.util.{EnvironmentTags, Filter}
import kamon.Kamon
import kamon.datadog.DatadogAPIReporter.Configuration
import kamon.module.{MetricReporter, ModuleFactory}
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

class DatadogAPIReporterFactory extends ModuleFactory {
  override def create(settings: ModuleFactory.Settings): DatadogAPIReporter = {
    val config = DatadogAPIReporter.readConfiguration(settings.config)
    new DatadogAPIReporter(config)
  }
}

class DatadogAPIReporter(@volatile private var configuration: Configuration) extends MetricReporter {
  import DatadogAPIReporter._

  private val logger = LoggerFactory.getLogger(classOf[DatadogAPIReporter])
  private val symbols = DecimalFormatSymbols.getInstance(Locale.US)
  symbols.setDecimalSeparator('.') // Just in case there is some weird locale config we are not aware of.

  private val valueFormat = new DecimalFormat("#0.#########", symbols)

  logger.info("Started the Datadog API reporter.")

  override def stop(): Unit = {
    logger.info("Stopped the Datadog API reporter.")
  }

  override def reconfigure(config: Config): Unit = {
    configuration = readConfiguration(config)
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    configuration.httpClient.doPost("application/json; charset=utf-8", buildRequestBody(snapshot)) match {
      case Failure(e) =>
        logger.logAtLevel(configuration.failureLogLevel, e.getMessage)
      case Success(response) =>
        logger.trace(response)
    }
  }

  private[datadog] def buildRequestBody(snapshot: PeriodSnapshot): Array[Byte] = {
    val timestamp = snapshot.from.getEpochSecond.toString

    val host = Kamon.environment.host
    val interval = Math.round(Duration.between(snapshot.from, snapshot.to).toMillis() / 1000d)

    val payloadBuilder = new StringBuilder()

    val apiVersion = configuration.apiVersion

    @inline
    def doubleToPercentileString(double: Double) = {
      if (double == double.toLong) f"${double.toLong}%d"
      else f"$double%s"
    }

    @inline
    def metricTypeJsonPart(metricTypeStr: String) = {
      if (apiVersion == "v1") {
        s"\"type\":\"$metricTypeStr\""
      }
      // v2 requires an enum type in the payload instead of the string name
      else {
        if (metricTypeStr == countMetricType) {
          "\"type\":1"
        } else if (metricTypeStr == gaugeMetricType) {
          "\"type\":3"
        } else {
          //  This reporter currently only supports counter and gauges.
          // `0` is an undefined metric type in Datadog API.
          "\"type\":0"
        }
      }
    }

    lazy val hostJsonPart = {
      if (apiVersion == "v1") {
        s"\"host\":\"$host\""
      }
      // v2 has a "resources" array field where "host" should be defined
      else {
        s"\"resources\":[{\"name\":\"$host\",\"type\":\"host\"}]"
      }
    }

    def addDistribution(metric: MetricSnapshot.Distributions): Unit = {
      val unit = metric.settings.unit
      metric.instruments.foreach { d =>
        val dist = d.value

        val average = if (dist.count > 0L) (dist.sum / dist.count) else 0L
        addMetric(metric.name + ".avg", valueFormat.format(scale(average, unit)), gaugeMetricType, d.tags)
        addMetric(metric.name + ".count", valueFormat.format(dist.count), countMetricType, d.tags)
        addMetric(
          metric.name + ".median",
          valueFormat.format(scale(dist.percentile(50d).value, unit)),
          gaugeMetricType,
          d.tags
        )
        configuration.percentiles.foreach { p =>
          addMetric(
            metric.name + s".${doubleToPercentileString(p)}percentile",
            valueFormat.format(scale(dist.percentile(p).value, unit)),
            gaugeMetricType,
            d.tags
          )
        }
        addMetric(metric.name + ".max", valueFormat.format(scale(dist.max, unit)), gaugeMetricType, d.tags)
        addMetric(metric.name + ".min", valueFormat.format(scale(dist.min, unit)), gaugeMetricType, d.tags)
      }
    }

    def addMetric(metricName: String, value: String, metricType: String, tags: TagSet): Unit = {
      val customTags = (configuration.extraTags ++ tags.iterator(_.toString).map(p => p.key -> p.value).filter(t =>
        configuration.tagFilter.accept(t._1)
      )).map { case (k, v) ⇒ quote"$k:$v" }

      val allTagsString = customTags.mkString("[", ",", "]")

      if (payloadBuilder.length() > 0) payloadBuilder.append(",")

      val point = if (apiVersion == "v1") {
        s"[$timestamp,$value]"
      } else {
        s"{\"timestamp\":$timestamp,\"value\":$value}"
      }

      payloadBuilder
        .append(s"""{"metric":"$metricName",${metricTypeJsonPart(
                    metricType
                  )},"interval":$interval,"points":[$point],"tags":$allTagsString,$hostJsonPart}""".stripMargin)
    }

    snapshot.counters.foreach { snap =>
      snap.instruments.foreach { instrument =>
        addMetric(
          snap.name,
          valueFormat.format(scale(instrument.value, snap.settings.unit)),
          countMetricType,
          instrument.tags
        )
      }
    }
    snapshot.gauges.foreach { snap =>
      snap.instruments.foreach { instrument =>
        addMetric(
          snap.name,
          valueFormat.format(scale(instrument.value, snap.settings.unit)),
          gaugeMetricType,
          instrument.tags
        )
      }
    }

    (snapshot.histograms ++ snapshot.rangeSamplers ++ snapshot.timers).foreach(addDistribution)

    payloadBuilder
      .insert(0, "{\"series\":[")
      .append("]}")
      .toString.getBytes(StandardCharsets.UTF_8)

  }

  private def scale(value: Double, unit: MeasurementUnit): Double = unit.dimension match {
    case Time if unit.magnitude != configuration.timeUnit.magnitude =>
      MeasurementUnit.convert(value, unit, configuration.timeUnit)

    case Information if unit.magnitude != configuration.informationUnit.magnitude =>
      MeasurementUnit.convert(value, unit, configuration.informationUnit)

    case _ => value
  }
}

private object DatadogAPIReporter {
  val countMetricType = "count"
  val gaugeMetricType = "gauge"

  private def configureHttpClient(config: Config): (String, HttpClient) = {
    val baseClient = buildHttpClient(config)

    val apiVersion = {
      val v = config.getString("version")
      if (v != "v1" && v != "v2") {
        sys.error(s"Invalid Datadog API version, the possible values are [v1, v2].")
      }
      v
    }

    val apiKey = config.getString("api-key")
    val apiUrl = {
      val url = config.getString("api-url")

      if (apiVersion == "v1") {
        url + "?api_key=" + apiKey
      } else {
        url
      }
    }
    val headers = {
      if (apiVersion == "v2") {
        List("DD-API-KEY" -> apiKey)
      } else List.empty
    }

    (apiVersion, baseClient.copy(endpoint = apiUrl, headers = headers))
  }

  case class Configuration(
    httpClient: HttpClient,
    apiVersion: String,
    percentiles: Set[Double],
    timeUnit: MeasurementUnit,
    informationUnit: MeasurementUnit,
    extraTags: Seq[(String, String)],
    tagFilter: Filter,
    failureLogLevel: Level
  )

  implicit class QuoteInterp(val sc: StringContext) extends AnyVal {
    def quote(args: Any*): String = "\"" + sc.s(args: _*) + "\""
  }

  def readConfiguration(config: Config): Configuration = {
    val datadogConfig = config.getConfig("kamon.datadog")
    val httpConfig = datadogConfig.getConfig("api")
    val (apiVersion, httpClient) = configureHttpClient(httpConfig)

    // Remove the "host" tag since it gets added to the datadog payload separately
    val extraTags = EnvironmentTags
      .from(Kamon.environment, datadogConfig.getConfig("environment-tags"))
      .without("host")
      .all()
      .map(p => p.key -> Tag.unwrapValue(p).toString)

    Configuration(
      httpClient = httpClient,
      apiVersion = apiVersion,
      percentiles = datadogConfig.getDoubleList("percentiles").asScala.toList.map(_.toDouble).toSet,
      timeUnit = readTimeUnit(datadogConfig.getString("time-unit")),
      informationUnit = readInformationUnit(datadogConfig.getString("information-unit")),
      // Remove the "host" tag since it gets added to the datadog payload separately
      extraTags = extraTags,
      tagFilter = Kamon.filter("kamon.datadog.environment-tags.filter"),
      failureLogLevel = readLogLevel(datadogConfig.getString("failure-log-level"))
    )
  }
}
