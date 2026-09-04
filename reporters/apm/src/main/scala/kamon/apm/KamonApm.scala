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

import java.time.{Duration, Instant}
import com.google.protobuf.ByteString
import com.typesafe.config.Config
import kamon.metric.{DynamicRange, Instrument, MetricSnapshot, PeriodSnapshot}
import kamon.metric.MeasurementUnit.{Dimension, information, time}
import kamon.module.{CombinedReporter, Module, ModuleFactory}
import kamon.status.BuildInfo
import kamon.tag.{Tag, TagSet}
import kamon.trace.Span
import kamon.util.UnitConverter

import java.util
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters.seqAsJavaListConverter

class KamonApm(configPath: String, executionContext: ExecutionContext) extends CombinedReporter {
  private val _maxSnapshotAge = Duration.ofMinutes(30)
  private var _settings = readSettings(Kamon.config(), configPath)
  private var _httpClient: Option[ApiClient] = Option(new ApiClient(_settings))
  private val _unitConverter = new UnitConverter(time.nanoseconds, information.bytes, DynamicRange.Default)
  private var _resource = buildResource()

  if (isAcceptableApiKey(_settings.apiKey)) {
    val serviceName = Kamon.environment.service
    _logger.info(
      s"Starting the Kamon APM Reporter. Your service will be displayed as [${serviceName}] at https://apm.kamon.io/"
    )
    Future {
      reportBoot()
    }(executionContext).failed.foreach(t => _logger.error("Failed to say Hello to Kamon APM", t))(executionContext)
  } else {
    _logger.warn(s"The Kamon APM Reporter was started with an invalid API key [${_settings.apiKey}]")
  }

  def this(executionContext: ExecutionContext) =
    this("kamon.apm", executionContext)

  override def stop(): Unit = {
    reportShutdown()
    _httpClient.foreach(_.stop)
    _logger.info("Stopped the Kamon APM Reporter.")
  }

  override def reconfigure(config: Config): Unit = {
    _settings = readSettings(config, configPath)
    _httpClient.foreach(_.stop)
    _httpClient = Option(new ApiClient(_settings))
    _resource = buildResource()
  }

  private def buildResource(): ingestion.v2.IngestionV2.Resource = {
    import ingestion.v2.IngestionV2.Tag

    val environment = Kamon.environment
    val customTags = toApmTags(environment.tags)
    ingestion.v2.IngestionV2.Resource.newBuilder()
      .addTags(Tag.newBuilder().setKey("host.name").setStringValue(environment.host).build())
      .addTags(Tag.newBuilder().setKey("service.name").setStringValue(environment.service).build())
      .addTags(Tag.newBuilder().setKey("service.instance.id").setStringValue(environment.instance).build())
      .addTags(Tag.newBuilder().setKey("telemetry.sdk.name").setStringValue("kamon").build())
      .addTags(Tag.newBuilder().setKey("telemetry.sdk.language").setStringValue("scala").build())
      .addTags(Tag.newBuilder().setKey("telemetry.sdk.version").setStringValue(BuildInfo.version).build())
      .addAllTags(customTags)
      .build()

  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val snapshotAge = java.time.Duration.between(snapshot.to, Kamon.clock().instant()).toMillis
    if (snapshotAge >= 0 && snapshotAge < _maxSnapshotAge.toMillis)
      if (isAcceptableApiKey(_settings.apiKey))
        reportMetricsBatch(snapshot)
      else
        _logger.error(s"Dropping metrics because an invalid API key has been configured [${_settings.apiKey}]")
    else
      _logger.warn(
        "Dropping stale metrics for period from: [{}], to: [{}]. The snapshot is [{} millis] old",
        snapshot.from.toEpochMilli().toString(),
        snapshot.to.toEpochMilli().toString(),
        snapshotAge.toString()
      )
  }

  override def reportSpans(spans: Seq[Span.Finished]): Unit = if (spans.nonEmpty) {
    if (isAcceptableApiKey(_settings.apiKey)) {
      val spansFinishLimit = Kamon.clock().instant().minus(_maxSnapshotAge)
      val recentSpans = spans.filter(_.to.isAfter(spansFinishLimit))

      if (recentSpans.nonEmpty) {
        val batch = ingestion.v2.IngestionV2.SpansBatch.newBuilder()
          .setApiKey(_settings.apiKey)
          .setResource(_resource)
          .addAllSpans(recentSpans.map(convertSpan).asJava)
          .build()

        _httpClient.foreach(_.postSpans(batch))
      }
    } else
      _logger.error(s"Dropping Spans because an invalid API key has been configured [${_settings.apiKey}]")
  }

  private def reportMetricsBatch(snapshot: PeriodSnapshot): Unit = {
    val fromEpoch = snapshot.from.getEpochSecond
    val endEpoch = snapshot.to.getEpochSecond

    val histograms = snapshot.histograms.map(h => toApmHistogram(h, fromEpoch, endEpoch))
    val timers = snapshot.timers.map(h => toApmHistogram(h, fromEpoch, endEpoch))
    val rangeSamplers = snapshot.rangeSamplers.map(h => toApmHistogram(h, fromEpoch, endEpoch))
    val gauges = snapshot.gauges.map(g => toApmGauge(g, fromEpoch, endEpoch))
    val counters = snapshot.counters.map(c => toApmCounter(c, fromEpoch, endEpoch))
    val allMetrics = histograms ++ timers ++ rangeSamplers ++ gauges ++ counters
    val metricsBatch = ingestion.v2.IngestionV2.MetricsBatch.newBuilder()
      .setApiKey(_settings.apiKey)
      .setResource(_resource)
      .addAllMetrics(allMetrics.asJava)
      .build()

    _httpClient.foreach(_.postIngestion(metricsBatch))

  }

  private def reportBoot(): Unit = {
    val hello = ingestion.v2.IngestionV2.Hello.newBuilder()
      .setApiKey(_settings.apiKey)
      .setResource(_resource)
      .build()

    _httpClient.foreach(_.postHello(hello))
  }

  private def reportShutdown(): Unit = {
    val goodBye = ingestion.v2.IngestionV2.Goodbye.newBuilder()
      .setApiKey(_settings.apiKey)
      .setResource(_resource)
      .build()

    _httpClient.foreach(_.postGoodbye(goodBye))
  }

  private def toApmCounter(
    metric: MetricSnapshot.Values[Long],
    fromEpoch: Long,
    toEpoch: Long
  ): ingestion.v2.IngestionV2.Metric = {
    val dataPoints = metric.instruments.map {
      case Instrument.Snapshot(tags, count) =>
        ingestion.v2.IngestionV2.Metric.DataPoint.newBuilder()
          .setPeriodStartEpoch(fromEpoch)
          .setPeriodEndEpoch(toEpoch)
          .addAllTags(toApmTags(tags))
          .setCounter(count)
          .build()
    }

    ingestion.v2.IngestionV2.Metric.newBuilder()
      .setName(metric.name)
      .setUnit(metric.settings.unit.magnitude.name)
      .setDescription(metric.description)
      .addAllDataPoints(dataPoints.asJava)
      .build()
  }

  private def toApmGauge(
    metric: MetricSnapshot.Values[Double],
    fromEpoch: Long,
    toEpoch: Long
  ): ingestion.v2.IngestionV2.Metric = {
    val dataPoints = metric.instruments.map {
      case Instrument.Snapshot(tags, value) =>
        ingestion.v2.IngestionV2.Metric.DataPoint.newBuilder()
          .setPeriodStartEpoch(fromEpoch)
          .setPeriodEndEpoch(toEpoch)
          .addAllTags(toApmTags(tags))
          .setGauge(value)
          .build()
    }

    ingestion.v2.IngestionV2.Metric.newBuilder()
      .setName(metric.name)
      .setUnit(metric.settings.unit.magnitude.name)
      .setDescription(metric.description)
      .addAllDataPoints(dataPoints.asJava)
      .build()
  }

  private def toApmHistogram(
    metric: MetricSnapshot.Distributions,
    fromEpoch: Long,
    toEpoch: Long
  ): ingestion.v2.IngestionV2.Metric = {
    val dataPoints = metric.instruments.map {
      case Instrument.Snapshot(tags, distribution) =>
        val convertedDistribution = _unitConverter.convertDistribution(distribution, metric.settings.unit)
        val indexes = new util.LinkedList[java.lang.Integer]()
        val counts = new util.LinkedList[java.lang.Long]()
        convertedDistribution.bucketsIterator.foreach { bucket =>
          indexes.add(countsArrayIndex(bucket.value))
          counts.add(bucket.frequency)
        }

        val apmHistogram = ingestion.v2.IngestionV2.Metric.DataPoint.Histogram.newBuilder()
          .addAllBucketIndex(indexes)
          .addAllBucketCount(counts)
          .build()

        ingestion.v2.IngestionV2.Metric.DataPoint.newBuilder()
          .setPeriodStartEpoch(fromEpoch)
          .setPeriodEndEpoch(toEpoch)
          .addAllTags(toApmTags(tags))
          .setHistogram(apmHistogram)
          .build()
    }

    val metricUnit = metric.settings.unit.dimension match {
      case Dimension.Time        => time.nanoseconds.magnitude.name
      case Dimension.Information => information.bytes.magnitude.name
      case _                     => metric.settings.unit.magnitude.name
    }

    ingestion.v2.IngestionV2.Metric.newBuilder()
      .setName(metric.name)
      .setUnit(metricUnit)
      .setDescription(metric.description)
      .addAllDataPoints(dataPoints.asJava)
      .build()
  }

  private def toEpochNanos(inst: Instant): Long = {
    TimeUnit.SECONDS.toNanos(inst.getEpochSecond()) + inst.getNano();
  }

  private def toApmTags(tagSet: TagSet): java.lang.Iterable[ingestion.v2.IngestionV2.Tag] = {
    import ingestion.v2.IngestionV2.{Tag => ApmTag}
    val tags = new util.LinkedList[ApmTag]()

    tagSet.iterator().foreach {
      case t: Tag.String  => tags.add(ApmTag.newBuilder().setKey(t.key).setStringValue(t.value).build())
      case t: Tag.Boolean => tags.add(ApmTag.newBuilder().setKey(t.key).setBoolValue(t.value).build())
      case t: Tag.Long    => tags.add(ApmTag.newBuilder().setKey(t.key).setLongValue(t.value).build())
    }

    tags
  }

  private def convertSpan(span: Span.Finished): ingestion.v2.IngestionV2.Span = {
    val events = new util.LinkedList[ingestion.v2.IngestionV2.Span.Event]()
    span.marks.foreach { m =>
      events.add(
        ingestion.v2.IngestionV2.Span.Event.newBuilder()
          .setName(m.key)
          .setHappenedAtEpochNanos(toEpochNanos(m.instant))
          .build()
      )
    }

    val links = new util.LinkedList[ingestion.v2.IngestionV2.Span.Link]()
    span.links.foreach { link =>
      links.add(
        ingestion.v2.IngestionV2.Span.Link.newBuilder()
          .setTraceId(ByteString.copyFrom(link.trace.id.bytes))
          .setSpanId(ByteString.copyFrom(link.spanId.bytes))
          .build()
      )
    }

    ingestion.v2.IngestionV2.Span.newBuilder()
      .setId(ByteString.copyFrom(span.id.bytes))
      .setParentId(ByteString.copyFrom(span.parentId.bytes))
      .setTraceId(ByteString.copyFrom(span.trace.id.bytes))
      .setStartEpochNanos(toEpochNanos(span.from))
      .setEndEpochNanos(toEpochNanos(span.to))
      .setName(span.operationName)
      .setKind(convertSpanKind(span.kind))
      .setStatus(convertSpanStatus(span))
      .addAllTags(toApmTags(span.tags))
      .addAllTags(toApmTags(span.metricTags))
      .addAllEvents(events)
      .addAllLinks(links)
      .build()
  }

  private def convertSpanKind(kind: Span.Kind): ingestion.v2.IngestionV2.Span.Kind = kind match {
    case Span.Kind.Server   => ingestion.v2.IngestionV2.Span.Kind.Server
    case Span.Kind.Client   => ingestion.v2.IngestionV2.Span.Kind.Client
    case Span.Kind.Producer => ingestion.v2.IngestionV2.Span.Kind.Producer
    case Span.Kind.Consumer => ingestion.v2.IngestionV2.Span.Kind.Consumer
    case Span.Kind.Internal => ingestion.v2.IngestionV2.Span.Kind.Internal
    case _                  => ingestion.v2.IngestionV2.Span.Kind.Unknown
  }

  private def convertSpanStatus(span: Span.Finished): ingestion.v2.IngestionV2.Span.Status = {
    if (span.hasError)
      ingestion.v2.IngestionV2.Span.Status.Error
    else
      ingestion.v2.IngestionV2.Span.Status.Ok
  }

}

object KamonApm {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new KamonApm(settings.executionContext)
  }
}
