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
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.time.Duration
import java.util.Locale

import com.typesafe.config.Config
import kamon.metric._
import kamon.metric.MeasurementUnit
import kamon.metric.MeasurementUnit.Dimension.{Information, Time}
import kamon.metric.MeasurementUnit.{information, time}
import kamon.{Kamon, MetricReporter}
import org.asynchttpclient.{DefaultAsyncHttpClient, DefaultAsyncHttpClientConfig}
import org.slf4j.LoggerFactory


class DatadogAPIReporter extends MetricReporter {
  private val logger = LoggerFactory.getLogger(classOf[DatadogAPIReporter])
  private val symbols = DecimalFormatSymbols.getInstance(Locale.US)
  symbols.setDecimalSeparator('.') // Just in case there is some weird locale config we are not aware of.
  private val valueFormat = new DecimalFormat("#0.#########", symbols)

  private var httpClient: Option[DefaultAsyncHttpClient] = None

  override def start(): Unit = {
    httpClient = Option(createHttpClient(readConfiguration(Kamon.config())))
    logger.info("Started the Datadog API reporter.")
  }

  override def stop(): Unit = {
    stopHttpClient()
  }

  override def reconfigure(config: Config): Unit = {
    if(httpClient.nonEmpty)
      stopHttpClient()

    httpClient = Option(createHttpClient(readConfiguration(config)))
  }


  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val config = readConfiguration(Kamon.config())
    val url = "https://app.datadoghq.com/api/v1/series?api_key=" + config.apiKey

    val client = new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(config.connectTimeout.toMillis.toInt)
      .setReadTimeout(config.connectTimeout.toMillis.toInt)
      .setRequestTimeout(config.connectTimeout.toMillis.toInt)
      .build())

    client.preparePost(url)
      .setBody(buildRequestBody(snapshot))
      .setHeader("Content-Type", "application/json")
      .execute()

  }

  private def buildRequestBody(snapshot: PeriodSnapshot): String = {
    val timestamp: Long = (snapshot.from.toEpochMilli)
    val serviceTag = "\"service:" + Kamon.environment.service + "\""
    val host = Kamon.environment.host
    val seriesBuilder = new StringBuilder()

    def addCounter(counter: MetricValue): Unit =
      addMetric(counter.name, valueFormat.format(scale(counter.value, counter.unit)), "counter", counter.tags)

    def addGauge(gauge: MetricValue): Unit =
      addMetric(gauge.name, valueFormat.format(scale(gauge.value, gauge.unit)), "gauge", gauge.tags)

    def addDistribution(metric: MetricDistribution): Unit = {
      import metric._

      addMetric(name + ".min", valueFormat.format(scale(distribution.min, unit)), "gauge", metric.tags)
      addMetric(name + ".max", valueFormat.format(scale(distribution.max, unit)), "gauge", metric.tags)
      addMetric(name + ".count", valueFormat.format(scale(distribution.count, unit)), "counter", metric.tags)
      addMetric(name + ".sum", valueFormat.format(scale(distribution.sum, unit)), "gauge", metric.tags)
      addMetric(name + ".p95", valueFormat.format(scale(distribution.percentile(95D).value, unit)), "gauge", metric.tags)

    }

    def addMetric(metricName: String, value: String, metricType: String, tags: Map[String, String]): Unit = {
      val customTags = tags.map { case (k, v) ⇒ "\"" + k + ":" + v + "\"" }.toSeq
      val allTagsString = (customTags :+ serviceTag).mkString("[", ",", "]")

      if(seriesBuilder.length() > 0)
        seriesBuilder.append(",")

      seriesBuilder
        .append("{\"metric\":\"").append(metricName).append("\",")
        .append("\"points\":[[").append(String.valueOf(timestamp)).append(",").append(value).append("]],")
        .append("\"type\":\"").append(metricType).append("\",")
        .append("\"host\":\"").append(host).append("\",")
        .append("\"tags\":").append(allTagsString).append("}")
    }


    for(counter <- snapshot.metrics.counters) {
      addCounter(counter)
    }

    for(gauge <- snapshot.metrics.gauges) {
      addGauge(gauge)
    }

    for(metric <- snapshot.metrics.histograms ++ snapshot.metrics.rangeSamplers) {
      addDistribution(metric)
    }

    "{\"series\":[" + seriesBuilder.toString() + "]}"
  }

  private def scale(value: Long, unit: MeasurementUnit): Double = unit.dimension match {
    case Time         if unit.magnitude != time.seconds       => MeasurementUnit.scale(value, unit, time.seconds)
    case Information  if unit.magnitude != information.bytes  => MeasurementUnit.scale(value, unit, information.bytes)
    case _ => value
  }

  private def createHttpClient(config: Configuration): DefaultAsyncHttpClient =
    new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(config.connectTimeout.toMillis.toInt)
      .setReadTimeout(config.connectTimeout.toMillis.toInt)
      .setRequestTimeout(config.connectTimeout.toMillis.toInt)
      .build())

  private def stopHttpClient(): Unit = {
    httpClient.foreach(_.close())
  }

  private def readConfiguration(config: Config): Configuration = {
    val datadogConfig = config.getConfig("kamon.datadog")

    Configuration(
      apiKey = datadogConfig.getString("http.api-key"),
      connectTimeout = datadogConfig.getDuration("http.connect-timeout"),
      readTimeout = datadogConfig.getDuration("http.read-timeout"),
      requestTimeout = datadogConfig.getDuration("http.request-timeout"),
      timeUnit = readTimeUnit(datadogConfig.getString("time-unit")),
      informationUnit = readInformationUnit(datadogConfig.getString("information-unit"))
    )
  }

  private case class Configuration(apiKey: String, connectTimeout: Duration, readTimeout: Duration, requestTimeout: Duration,
    timeUnit: MeasurementUnit, informationUnit: MeasurementUnit)

}

