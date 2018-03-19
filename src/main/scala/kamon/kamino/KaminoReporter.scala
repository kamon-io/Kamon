package kamon.kamino

import java.nio.ByteBuffer
import java.time.Duration

import com.google.protobuf
import kamino.IngestionV1
import kamino.IngestionV1.InstrumentType
import kamino.IngestionV1.InstrumentType.{COUNTER, GAUGE, HISTOGRAM, MIN_MAX_COUNTER}
import kamon.metric.SnapshotCreation.ZigZagCountsDistribution
import kamon.{Kamon, MetricReporter}
import kamon.metric._
import org.HdrHistogram.ZigZag
import org.slf4j.LoggerFactory

import scala.util.Try
import scala.collection.JavaConverters._


class KaminoReporter extends MetricReporter {
  private val MaxAge = Duration.ofMinutes(30)
  private val logger = LoggerFactory.getLogger(classOf[KaminoReporter])
  private var httpClient: Option[KaminoApiClient] = None
  private val metricScaler = new Scaler(MeasurementUnit.time.nanoseconds, MeasurementUnit.information.bytes, DynamicRange.Default)
  private val valueBuffer = ByteBuffer.wrap(Array.ofDim[Byte](16))
  private var configuration = readConfiguration(Kamon.config())
  private val periodAccumulator = new PeriodSnapshotAccumulator(Duration.ofSeconds(60), Duration.ofSeconds(1))

  override def start(): Unit = {
    httpClient = Option(new KaminoApiClient(readConfiguration(Kamon.config())))
    logger.info("Started the Kamino reporter.")
    Try(reportBoot(Kamon.clock().millis()))
  }

  override def stop(): Unit = {
    reportShutdown(Kamon.clock().millis())
    httpClient.foreach(_.stop)
    logger.info("Stopped the Kamino reporter.")
  }

  override def reconfigure(config: com.typesafe.config.Config): Unit = {
    val newConfiguration = readConfiguration(config)
    httpClient.foreach(_.stop)
    httpClient = Option(new KaminoApiClient(newConfiguration))
    configuration = newConfiguration
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val snapshotAge = java.time.Duration.between(snapshot.to, Kamon.clock().instant()).toMillis
    if(snapshotAge >= 0 && snapshotAge < MaxAge.toMillis) reportIngestion(snapshot)
  }

  private def reportIngestion(snapshot: PeriodSnapshot): Unit = {
    periodAccumulator.add(snapshot).foreach { accumulatedSnapshot =>
      val histograms = accumulatedSnapshot.metrics.histograms.map(toIngestionMetricDistribution(HISTOGRAM))
      val mmCounters = accumulatedSnapshot.metrics.rangeSamplers.map(toIngestionMetricDistribution(MIN_MAX_COUNTER))
      val gauges = accumulatedSnapshot.metrics.gauges.map(toIngestionMetricValue(GAUGE))
      val counters = accumulatedSnapshot.metrics.counters.map(toIngestionMetricValue(COUNTER))

      val allMetrics = histograms ++ mmCounters ++ gauges ++ counters
      val interval = IngestionV1.Interval.newBuilder()
        .setFrom(accumulatedSnapshot.from.toEpochMilli)
        .setTo(accumulatedSnapshot.to.toEpochMilli)

      val batch = IngestionV1.MetricBatch.newBuilder()
        .setInterval(interval)
        .setApiKey( configuration.apiKey)
        .setService(Kamon.environment.service)
        .setHost(Kamon.environment.host)
        .setInstance(Kamon.environment.instance)
        .addAllMetrics(allMetrics.asJava)
        .build()

      httpClient.foreach(_.postIngestion(batch))
    }

  }

  private def nodeIdentity(): IngestionV1.NodeIdentity = {
    val env = Kamon.environment

    IngestionV1.NodeIdentity.newBuilder()
      .setService(env.service)
      .setInstance(env.instance)
      .setHost(env.host)
      .setApiKey(configuration.apiKey)
      .build()
  }

  private def reportBoot(initializationTimestamp: Long): Unit = {
    val hello = IngestionV1.Hello.newBuilder()
      .setNode(nodeIdentity)
      .setTime(initializationTimestamp)
      .setIncarnation(Kamon.environment.incarnation)
      .setVersion(configuration.appVersion)
      .build()

    httpClient.foreach(_.postHello(hello))
  }

  private def reportShutdown(shutdownTimestamp: Long): Unit = {
    val goodBye = IngestionV1.Goodbye.newBuilder()
      .setNode(nodeIdentity)
      .setTime(shutdownTimestamp)
      .build()

    httpClient.foreach(_.postGoodbye(goodBye))
  }

  private def toIngestionMetricValue(metricType: InstrumentType)(metric: MetricValue): IngestionV1.Metric = {
    valueBuffer.clear()
    ZigZag.putLong(valueBuffer, metricScaler.scaleMetricValue(metric).value)
    valueBuffer.flip()

    IngestionV1.Metric.newBuilder()
      .setName(metric.name)
      .putAllTags(metric.tags.asJava)
      .setInstrumentType(metricType)
      .setData(protobuf.ByteString.copyFrom(valueBuffer))
      .build()
  }


  private def toIngestionMetricDistribution(metricType: InstrumentType)(metric: MetricDistribution): IngestionV1.Metric = {
    val counts = metricScaler.scaleDistribution(metric).distribution.asInstanceOf[ZigZagCountsDistribution].zigZagCounts
    val privateCounts = ByteBuffer.wrap(counts.array())

    IngestionV1.Metric.newBuilder()
      .setName(metric.name)
      .putAllTags(metric.tags.asJava)
      .setInstrumentType(metricType)
      .setData(protobuf.ByteString.copyFrom(privateCounts))
      .build()
  }
}
