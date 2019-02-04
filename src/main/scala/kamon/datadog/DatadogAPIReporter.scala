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
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import kamon.metric._
import kamon.metric.MeasurementUnit
import kamon.metric.MeasurementUnit.Dimension.{ Information, Time }
import kamon.metric.MeasurementUnit.{ information, time }
import kamon.util.{ EnvironmentTagBuilder, Matcher }
import kamon.{ Kamon, MetricReporter }
import okhttp3.{ MediaType, OkHttpClient, Request, RequestBody }
import org.slf4j.LoggerFactory

class DatadogAPIReporter extends MetricReporter {
  import DatadogAPIReporter._

  private val logger = LoggerFactory.getLogger(classOf[DatadogAPIReporter])
  private val symbols = DecimalFormatSymbols.getInstance(Locale.US)
  symbols.setDecimalSeparator('.') // Just in case there is some weird locale config we are not aware of.

  private val jsonType = MediaType.parse("application/json; charset=utf-8")
  private val valueFormat = new DecimalFormat("#0.#########", symbols)
  private var configuration = readConfiguration(Kamon.config())
  private var httpClient: OkHttpClient = createHttpClient(configuration)

  override def start(): Unit = {
    logger.info("Started the Datadog API reporter.")
  }

  override def stop(): Unit = {
    logger.info("Stopped the Datadog API reporter.")
  }

  override def reconfigure(config: Config): Unit = {
    val newConfiguration = readConfiguration(config)
    httpClient = createHttpClient(readConfiguration(Kamon.config()))
    configuration = newConfiguration
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val body = RequestBody.create(jsonType, buildRequestBody(snapshot))
    val request = new Request.Builder().url(configuration.apiUrl + configuration.apiKey).post(body).build
    val response = httpClient.newCall(request).execute()

    if (!response.isSuccessful()) {
      logger.error(s"Failed to POST metrics to Datadog with status code [${response.code()}], Body: [${response.body().string()}]")
    }

    response.close()
  }

  private[datadog] def buildRequestBody(snapshot: PeriodSnapshot): String = {
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
      .toString
  }

  private def scale(value: Long, unit: MeasurementUnit): Double = unit.dimension match {
    case Time if unit.magnitude != configuration.timeUnit.magnitude =>
      MeasurementUnit.scale(value, unit, configuration.timeUnit)

    case Information if unit.magnitude != configuration.informationUnit.magnitude =>
      MeasurementUnit.scale(value, unit, configuration.informationUnit)

    case _ => value.toDouble
  }

  // Apparently okhttp doesn't require explicit closing of the connection
  private def createHttpClient(config: Configuration): OkHttpClient = {
    new OkHttpClient.Builder()
      .connectTimeout(config.connectTimeout.toMillis, TimeUnit.MILLISECONDS)
      .readTimeout(config.connectTimeout.toMillis, TimeUnit.MILLISECONDS)
      .writeTimeout(config.connectTimeout.toMillis, TimeUnit.MILLISECONDS).build()
  }

  private def readConfiguration(config: Config): Configuration = {
    val datadogConfig = config.getConfig("kamon.datadog")

    Configuration(
      apiUrl = datadogConfig.getString("http.api-url"),
      apiKey = datadogConfig.getString("http.api-key"),
      connectTimeout = datadogConfig.getDuration("http.connect-timeout"),
      readTimeout = datadogConfig.getDuration("http.read-timeout"),
      requestTimeout = datadogConfig.getDuration("http.request-timeout"),
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

  case class Configuration(apiUrl: String, apiKey: String, connectTimeout: Duration, readTimeout: Duration, requestTimeout: Duration,
                           timeUnit: MeasurementUnit, informationUnit: MeasurementUnit, extraTags: Map[String, String], tagFilter: Matcher)

  implicit class QuoteInterp(val sc: StringContext) extends AnyVal {
    def quote(args: Any*): String = "\"" + sc.s(args: _*) + "\""
  }
}
