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
import java.text.{ DecimalFormat, DecimalFormatSymbols }
import java.time.Duration
import java.util.Locale

import com.typesafe.config.Config
import io.netty.handler.codec.http.{ HttpHeaderNames, HttpHeaderValues }
import kamon.metric._
import kamon.metric.MeasurementUnit
import kamon.metric.MeasurementUnit.Dimension.{ Information, Time }
import kamon.metric.MeasurementUnit.{ information, time }
import kamon.{ Kamon, MetricReporter }
import org.asynchttpclient.{ DefaultAsyncHttpClient, DefaultAsyncHttpClientConfig }
import org.slf4j.LoggerFactory

class DatadogAPIReporter extends MetricReporter {
  import DatadogAPIReporter._

  private val logger = LoggerFactory.getLogger(classOf[DatadogAPIReporter])
  private val symbols = DecimalFormatSymbols.getInstance(Locale.US)

  symbols.setDecimalSeparator('.') // Just in case there is some weird locale config we are not aware of.

  private val valueFormat = new DecimalFormat("#0.#########", symbols)

  private var httpClient: Option[DefaultAsyncHttpClient] = None

  private var configuration = readConfiguration(Kamon.config())

  override def start(): Unit = {
    httpClient = Option(createHttpClient(readConfiguration(Kamon.config())))
    logger.info("Started the Datadog API reporter.")
  }

  override def stop(): Unit = {
    httpClient.foreach(_.close())
    logger.info("Stopped the Datadog API reporter.")
  }

  override def reconfigure(config: Config): Unit = {
    val newConfiguration = readConfiguration(config)
    httpClient.foreach(_.close())
    httpClient = Option(createHttpClient(newConfiguration))
    configuration = newConfiguration
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    httpClient.foreach(_.preparePost(apiUrl + configuration.apiKey)
      .setBody(buildRequestBody(snapshot))
      .setHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
      .execute())
  }

  private def buildRequestBody(snapshot: PeriodSnapshot): String = {
    val timestamp = snapshot.from.getEpochSecond.toString
    val serviceTag = quote"service:${Kamon.environment.service}"
    val host = Kamon.environment.host
    val seriesBuilder = new StringBuilder()

    def addDistribution(metric: MetricDistribution): Unit = {
      import metric._

      addMetric(name + ".min", valueFormat.format(scale(distribution.min, unit)), gauge, metric.tags)
      addMetric(name + ".max", valueFormat.format(scale(distribution.max, unit)), gauge, metric.tags)
      addMetric(name + ".count", valueFormat.format(scale(distribution.count, unit)), count, metric.tags)
      addMetric(name + ".sum", valueFormat.format(scale(distribution.sum, unit)), gauge, metric.tags)
      addMetric(name + ".p95", valueFormat.format(scale(distribution.percentile(95D).value, unit)), gauge, metric.tags)
    }

    def addMetric(metricName: String, value: String, metricType: String, tags: Map[String, String]): Unit = {
      val customTags = tags.map { case (k, v) ⇒ quote"$k:$v" }.toSeq
      val allTagsString = (customTags :+ serviceTag).mkString("[", ",", "]")

      if (seriesBuilder.length() > 0) seriesBuilder.append(",")

      seriesBuilder
        .append(s"""{"metric":"$metricName","points":[[$timestamp,$value]],"type":"$metricType","host":"$host","tags":$allTagsString}""")
    }

    def add(metric: MetricValue, metricType: String): Unit =
      addMetric(metric.name, valueFormat.format(scale(metric.value, metric.unit)), metricType, metric.tags)

    snapshot.metrics.counters.foreach(add(_, count))
    snapshot.metrics.gauges.foreach(add(_, gauge))

    (snapshot.metrics.histograms ++ snapshot.metrics.rangeSamplers).foreach(addDistribution)

    seriesBuilder
      .insert(0, "{\"series\":[")
      .append("]}")
      .toString
  }

  private def scale(value: Long, unit: MeasurementUnit): Double = unit.dimension match {
    case Time if unit.magnitude != time.seconds.magnitude             => MeasurementUnit.scale(value, unit, time.seconds)
    case Information if unit.magnitude != information.bytes.magnitude => MeasurementUnit.scale(value, unit, information.bytes)
    case _                                                            => value.toDouble
  }

  private def createHttpClient(config: Configuration): DefaultAsyncHttpClient =
    new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(config.connectTimeout.toMillis.toInt)
      .setReadTimeout(config.connectTimeout.toMillis.toInt)
      .setRequestTimeout(config.connectTimeout.toMillis.toInt)
      .build())

  private def readConfiguration(config: Config): Configuration = {
    val datadogConfig = config.getConfig("kamon.datadog")

    Configuration(
      apiKey = datadogConfig.getString("http.api-key"),
      connectTimeout = datadogConfig.getDuration("http.connect-timeout"),
      readTimeout = datadogConfig.getDuration("http.read-timeout"),
      requestTimeout = datadogConfig.getDuration("http.request-timeout"),
      timeUnit = readTimeUnit(datadogConfig.getString("time-unit")),
      informationUnit = readInformationUnit(datadogConfig.getString("information-unit")))
  }
}

private object DatadogAPIReporter {
  val apiUrl = "https://app.datadoghq.com/api/v1/series?api_key="

  val count = "count"
  val gauge = "gauge"

  case class Configuration(apiKey: String, connectTimeout: Duration, readTimeout: Duration, requestTimeout: Duration,
                           timeUnit: MeasurementUnit, informationUnit: MeasurementUnit)

  implicit class QuoteInterp(val sc: StringContext) extends AnyVal {
    def quote(args: Any*): String = "\"" + sc.s(args: _*) + "\""
  }
}
