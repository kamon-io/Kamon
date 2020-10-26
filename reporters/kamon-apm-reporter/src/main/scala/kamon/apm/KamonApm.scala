/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

package kamon
package apm

import java.nio.ByteBuffer
import java.time.Duration

import com.google.protobuf
import com.typesafe.config.Config
import kamino.IngestionV1
import kamino.IngestionV1.InstrumentType.{COUNTER, GAUGE, HISTOGRAM, MIN_MAX_COUNTER}
import kamino.IngestionV1._
import org.HdrHistogram.ZigZag
import kamon.metric.Distribution.ZigZagCounts
import kamon.metric.{DynamicRange, Instrument, MetricSnapshot, PeriodSnapshot}
import kamon.metric.MeasurementUnit.{information, time}
import kamon.module.{CombinedReporter, Module, ModuleFactory}
import kamon.tag.TagSet
import kamon.trace.Span
import kamon.util.{Clock, UnitConverter}

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try


class KamonApm(configPath: String) extends CombinedReporter {
  private val _maxSnapshotAge = Duration.ofMinutes(30)
  private var _settings = readSettings(Kamon.config(), configPath)
  private var _httpClient: Option[KamonApmApiClient] = Option(new KamonApmApiClient(_settings))
  private val _valueBuffer = ByteBuffer.wrap(Array.ofDim[Byte](16))
  private val _accumulator = PeriodSnapshot.accumulator(Duration.ofSeconds(60), Duration.ofSeconds(1))
  private val _unitConverter = new UnitConverter(time.nanoseconds, information.bytes, DynamicRange.Default)

  if(isAcceptableApiKey(_settings.apiKey)) {
    val serviceName = Kamon.environment.service
    _logger.info(s"Starting the Kamon APM Reporter. Your service will be displayed as [${serviceName}] at https://apm.kamon.io/")
    Try(reportBoot(Kamon.clock().millis())).failed.foreach(t => _logger.error("Failed boot", t))
  } else {
    _logger.warn(s"The Kamon APM Reporter was started with an invalid API key [${_settings.apiKey}]")
  }

  def this() =
    this("kamon.apm")

  override def stop(): Unit = {
    reportShutdown(Kamon.clock().millis())
    _httpClient.foreach(_.stop)
    _logger.info("Stopped the Kamon APM Reporter.")
  }

  override def reconfigure(config: Config): Unit = {
    _settings = readSettings(config, configPath)
    _httpClient.foreach(_.stop)
    _httpClient = Option(new KamonApmApiClient(_settings))
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val snapshotAge = java.time.Duration.between(snapshot.to, Kamon.clock().instant()).toMillis
    if(snapshotAge >= 0 && snapshotAge < _maxSnapshotAge.toMillis)
      if(isAcceptableApiKey(_settings.apiKey))
        reportIngestion(snapshot)
      else
        _logger.error(s"Dropping metrics because an invalid API key has been configured [${_settings.apiKey}]")
    else
      _logger.warn("Dropping stale metrics for period from: [{}], to: [{}]. The snapshot is [{} millis] old",
        snapshot.from.toEpochMilli().toString(), snapshot.to.toEpochMilli().toString(), snapshotAge.toString())
  }

  override def reportSpans(spans: Seq[Span.Finished]): Unit = if(spans.nonEmpty) {
    if(isAcceptableApiKey(_settings.apiKey)) {
      val env = Kamon.environment
      val apmSpans = spans map convertSpan

      val batch = SpanBatch.newBuilder()
        .setAgent("kamon-2.x")
        .setServiceName(env.service)
        .setHost(env.host)
        .setInstance(env.instance)
        .setApiKey(_settings.apiKey)
        .addAllSpans(apmSpans.asJava)
        .build()

      _httpClient.foreach(_.postSpans(batch))

    } else
      _logger.error(s"Dropping Spans because an invalid API key has been configured [${_settings.apiKey}]")
  }

  private def reportIngestion(snapshot: PeriodSnapshot): Unit = {
    _accumulator.add(snapshot).foreach { accumulatedSnapshot =>
      val histograms = accumulatedSnapshot.histograms.flatMap(toIngestionMetricDistribution(HISTOGRAM))
      val timers = accumulatedSnapshot.timers.flatMap(toIngestionMetricDistribution(HISTOGRAM))
      val rangeSamplers = accumulatedSnapshot.rangeSamplers.flatMap(toIngestionMetricDistribution(MIN_MAX_COUNTER))
      val gauges = accumulatedSnapshot.gauges.flatMap(toIngestionMetricFloatingPointValue(GAUGE))
      val counters = accumulatedSnapshot.counters.flatMap(toIngestionMetricValue(COUNTER))

      val plan = Plan.METRIC_TRACING
      val allMetrics = histograms ++ timers ++ rangeSamplers ++ gauges ++ counters
      val interval = IngestionV1.Interval.newBuilder()
        .setFrom(accumulatedSnapshot.from.toEpochMilli)
        .setTo(accumulatedSnapshot.to.toEpochMilli)

      val batch = IngestionV1.MetricBatch.newBuilder()
        .setInterval(interval)
        .setApiKey(_settings.apiKey)
        .setAgent("kamon-2.x")
        .setService(Kamon.environment.service)
        .setHost(Kamon.environment.host)
        .setInstance(Kamon.environment.instance)
        .addAllMetrics(allMetrics.asJava)
        .setPlan(plan)
        .build()

      _httpClient.foreach(_.postIngestion(batch))
    }
  }

  private def reportBoot(initializationTimestamp: Long): Unit = {
    val hello = IngestionV1.Hello.newBuilder()
      .setNode(nodeIdentity)
      .setTime(initializationTimestamp)
      .setIncarnation(Kamon.environment.incarnation)
      .setVersion(_settings.appVersion)
      .build()

    _httpClient.foreach(_.postHello(hello))
  }

  private def reportShutdown(shutdownTimestamp: Long): Unit = {
    val goodBye = IngestionV1.Goodbye.newBuilder()
      .setNode(nodeIdentity)
      .setTime(shutdownTimestamp)
      .build()

    _httpClient.foreach(_.postGoodbye(goodBye))
  }

  private def nodeIdentity(): IngestionV1.NodeIdentity = {
    val env = Kamon.environment

    IngestionV1.NodeIdentity.newBuilder()
      .setService(env.service)
      .setInstance(env.instance)
      .setHost(env.host)
      .setApiKey(_settings.apiKey)
      .build()
  }

  private def toIngestionMetricValue(metricType: InstrumentType)(metric: MetricSnapshot.Values[Long]): Seq[IngestionV1.Metric] =
    metric.instruments.map {
      case Instrument.Snapshot(tags, value) =>
        _valueBuffer.clear()
        ZigZag.putLong(_valueBuffer, _unitConverter.convertValue(value, metric.settings.unit).toLong)
        _valueBuffer.flip()

        IngestionV1.Metric.newBuilder()
          .setName(metric.name)
          .putAllTags(stringifyTags(tags))
          .setInstrumentType(metricType)
          .setData(protobuf.ByteString.copyFrom(_valueBuffer))
          .build()
    }

  private def toIngestionMetricFloatingPointValue(metricType: InstrumentType)(metric: MetricSnapshot.Values[Double]): Seq[IngestionV1.Metric] =
    metric.instruments.map {
      case Instrument.Snapshot(tags, value) =>
        _valueBuffer.clear()
        // TODO: Keep the decimal parts of the gauge when reporting data to APM.
        val convertedValue = _unitConverter.convertValue(value, metric.settings.unit).toLong
        val offset = countsArrayIndex(convertedValue)

        if(offset > 0) ZigZag.putLong(_valueBuffer, -offset)
        ZigZag.putLong(_valueBuffer, 1)
        _valueBuffer.flip()

        IngestionV1.Metric.newBuilder()
          .setName(metric.name)
          .putAllTags(stringifyTags(tags))
          .setInstrumentType(metricType)
          .setData(protobuf.ByteString.copyFrom(_valueBuffer))
          .build()
    }

  private def toIngestionMetricDistribution(metricType: InstrumentType)(metric: MetricSnapshot.Distributions): Seq[IngestionV1.Metric] =
    metric.instruments.map {
      case Instrument.Snapshot(tags, distribution) =>
        val convertedDistribution = _unitConverter.convertDistribution(distribution, metric.settings.unit)
        val counts = convertedDistribution.asInstanceOf[ZigZagCounts].countsArray()

        IngestionV1.Metric.newBuilder()
          .setName(metric.name)
          .putAllTags(stringifyTags(tags))
          .setInstrumentType(metricType)
          .setData(protobuf.ByteString.copyFrom(counts))
          .build()
    }

  private def convertSpan(span: Span.Finished): IngestionV1.Span = {
    val marks = span.marks.map { m =>
      IngestionV1.Mark
        .newBuilder()
        .setInstant(m.instant.toEpochMilli)
        .setKey(m.key)
        .build()
    }

    val links = span.links.map { link =>
      IngestionV1.Link
        .newBuilder()
        .setKind(LinkKind.FOLLOWS_FROM)
        .setTraceId(link.trace.id.string)
        .setSpanId(link.spanId.string)
        .build()
    }

    val tags = stringifyTags(span.tags)
    val metricTags = stringifyTags(span.metricTags)
    IngestionV1.Span.newBuilder()
      .setId(span.id.string)
      .setTraceId(span.trace.id.string)
      .setParentId(span.parentId.string)
      .setOperationName(span.operationName)
      .setStartMicros(Clock.toEpochMicros(span.from))
      .setEndMicros(Clock.toEpochMicros(span.to))
      .putAllTags(tags)
      .putAllMetricTags(metricTags)
      .addAllMarks(marks.asJava)
      .setHasError(span.hasError)
      .setWasDelayed(span.wasDelayed)
      .setKind(convertSpanKind(span.kind))
      .setPosition(convertSpanPosition(span.position))
      .addAllLinks(links.asJava)
      .build()
  }

  private def stringifyTags(tags: TagSet): java.util.Map[String, String] = {
    val javaMap = new java.util.HashMap[String, String]()
    tags.iterator(_.toString).foreach(pair => {
      javaMap.put(pair.key, pair.value)
    })

    javaMap
  }

  private def convertSpanKind(kind: Span.Kind): SpanKind = kind match {
    case Span.Kind.Server   => SpanKind.SERVER
    case Span.Kind.Client   => SpanKind.CLIENT
    case Span.Kind.Producer => SpanKind.PRODUCER
    case Span.Kind.Consumer => SpanKind.CONSUMER
    case Span.Kind.Internal => SpanKind.INTERNAL
    case Span.Kind.Unknown  => SpanKind.UNKNOWN
  }

  private def convertSpanPosition(position: Span.Position): SpanPosition = position match {
    case Span.Position.Root       => SpanPosition.ROOT
    case Span.Position.LocalRoot  => SpanPosition.LOCAL_ROOT
    case Span.Position.Unknown    => SpanPosition.POSITION_UNKNOWN
  }
}


object KamonApm {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new KamonApm()
  }
}

