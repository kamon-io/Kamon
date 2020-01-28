/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon

import java.time.{Duration, Instant}
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import java.util.concurrent._

import com.typesafe.config.Config
import kamon.metric._
import kamon.trace.Span
import kamon.trace.Span.FinishedSpan
import kamon.util.{Clock, DynamicAccess, Registration}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import scala.util.Try
import scala.util.control.NonFatal
import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._

sealed trait Reporter {
  def start(): Unit
  def stop(): Unit
  def reconfigure(config: Config): Unit
}

trait MetricReporter extends Reporter {
  def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit
}

trait SpanReporter extends Reporter {
  def reportSpans(spans: Seq[Span.FinishedSpan]): Unit
}

trait ReporterRegistry {
  def loadReportersFromConfig(): Unit

  def addReporter(reporter: MetricReporter): Registration
  def addReporter(reporter: MetricReporter, name: String): Registration
  def addReporter(reporter: MetricReporter, name: String, filter: String): Registration
  def addReporter(reporter: SpanReporter): Registration
  def addReporter(reporter: SpanReporter, name: String): Registration

  def stopAllReporters(): Future[Unit]
}

object ReporterRegistry {

  private[kamon] trait SpanSink {
    def reportSpan(finishedSpan: FinishedSpan): Unit
  }

  private[kamon] class Default(metrics: MetricsSnapshotGenerator, initialConfig: Config, clock: Clock) extends ReporterRegistry with SpanSink {
    private val logger = LoggerFactory.getLogger(classOf[ReporterRegistry])
    private val registryExecutionContext = Executors.newScheduledThreadPool(2, threadFactory("kamon-reporter-registry", daemon = true))
    private val reporterCounter = new AtomicLong(0L)
    private var registryConfiguration = readRegistryConfiguration(initialConfig)

    private val metricReporters = TrieMap[Long, MetricReporterEntry]()
    private val metricReporterTickerSchedule = new AtomicReference[ScheduledFuture[_]]()
    private val spanReporters = TrieMap[Long, SpanReporterEntry]()
    private val spanReporterTickerSchedule = new AtomicReference[ScheduledFuture[_]]()


    reconfigure(initialConfig)

    override def loadReportersFromConfig(): Unit = {
      if(registryConfiguration.configuredReporters.isEmpty)
        logger.info("The kamon.reporters setting is empty, no reporters have been started.")
      else {
        registryConfiguration.configuredReporters.foreach { reporterFQCN =>
          val dynamicAccess = new DynamicAccess(getClass.getClassLoader)
          dynamicAccess.createInstanceFor[Reporter](reporterFQCN, Nil).map({
            case mr: MetricReporter =>
              addMetricReporter(mr, "loaded-from-config: " + reporterFQCN)
              logger.info("Loaded metric reporter [{}]", reporterFQCN)

            case sr: SpanReporter =>
              addSpanReporter(sr, "loaded-from-config: " + reporterFQCN)
              logger.info("Loaded span reporter [{}]", reporterFQCN)

          }).failed.foreach {
            t => logger.error(s"Failed to load configured reporter [$reporterFQCN]", t)
          }
        }
      }
    }

    override def addReporter(reporter: MetricReporter): Registration =
      addMetricReporter(reporter, reporter.getClass.getName())

    override def addReporter(reporter: MetricReporter, name: String): Registration =
      addMetricReporter(reporter, name)

    override def addReporter(reporter: MetricReporter, name: String, filter: String): Registration =
      addMetricReporter(reporter, name, Some(filter))

    override def addReporter(reporter: SpanReporter): Registration =
      addSpanReporter(reporter, reporter.getClass.getName())

    override def addReporter(reporter: SpanReporter, name: String): Registration =
      addSpanReporter(reporter, name)


    private def addMetricReporter(reporter: MetricReporter, name: String, filter: Option[String] = None): Registration = synchronized {
      val executor = Executors.newSingleThreadExecutor(threadFactory(name))
      val reporterEntry = new MetricReporterEntry(
        id = reporterCounter.getAndIncrement(),
        name = name,
        reporter = reporter,
        filter = filter,
        executionContext = ExecutionContext.fromExecutorService(executor)
      )

      Future {
        Try {
          reporterEntry.reporter.start()
        }.failed.foreach { error =>
          logger.error(s"Metric reporter [$name] failed to start.", error)
        }
      }(reporterEntry.executionContext)

      if(metricReporters.isEmpty)
        reStartMetricTicker()

      metricReporters.put(reporterEntry.id, reporterEntry)
      createRegistration(reporterEntry.id, metricReporters)

    }

    private def addSpanReporter(reporter: SpanReporter, name: String): Registration = synchronized {
      val executor = Executors.newSingleThreadExecutor(threadFactory(name))
      val reporterEntry = new SpanReporterEntry(
        id = reporterCounter.incrementAndGet(),
        name = name,
        reporter = reporter,
        bufferCapacity = registryConfiguration.traceReporterQueueSize,
        executionContext = ExecutionContext.fromExecutorService(executor)
      )

      Future {
        Try {
          reporterEntry.reporter.start()
        }.failed.foreach { error =>
          logger.error(s"Span reporter [$name] failed to start.", error)
        }
      }(reporterEntry.executionContext)

      if(spanReporters.isEmpty)
        reStartTraceTicker()

      spanReporters.put(reporterEntry.id, reporterEntry)
      createRegistration(reporterEntry.id, spanReporters)
    }

    private def createRegistration(id: Long, target: TrieMap[Long, _]): Registration = new Registration {
      override def cancel(): Boolean =
        target.remove(id).nonEmpty
    }

    override def stopAllReporters(): Future[Unit] = {
      implicit val stopReporterExeContext = ExecutionContext.fromExecutor(registryExecutionContext)
      val reporterStopFutures = Vector.newBuilder[Future[Unit]]

      while(metricReporters.nonEmpty) {
        val (idToRemove, _) = metricReporters.head
        metricReporters.remove(idToRemove).foreach { entry =>
          reporterStopFutures += stopMetricReporter(entry)
        }
      }

      while(spanReporters.nonEmpty) {
        val (idToRemove, _) = spanReporters.head
        spanReporters.remove(idToRemove).foreach { entry =>
          reporterStopFutures += stopSpanReporter(entry)
        }
      }

      Future.sequence(reporterStopFutures.result()).map(_ => ())
    }

    private[kamon] def reconfigure(config: Config): Unit = synchronized {
      val newConfig = readRegistryConfiguration(config)

      if(newConfig.metricTickInterval != registryConfiguration.metricTickInterval && metricReporters.nonEmpty)
        reStartMetricTicker()

      if(newConfig.traceTickInterval != registryConfiguration.traceTickInterval && spanReporters.nonEmpty)
        reStartTraceTicker()

      // Reconfigure all registered reporters
      metricReporters.foreach {
        case (_, entry) =>
          Future {
            Try {
              entry.reporter.reconfigure(config)
            }.failed.foreach { error =>
              logger.error(s"Metric reporter [${entry.name}] failed to reconfigure.", error)
            }
          }(entry.executionContext)
      }
      spanReporters.foreach   {
        case (_, entry) =>
          Future {
            Try {
              entry.reporter.reconfigure(config)
            }.failed.foreach { error =>
              logger.error(s"Span reporter [${entry.name}] failed to reconfigure.", error)
            }
          }(entry.executionContext)
      }

      registryConfiguration = newConfig
    }


    private def reStartMetricTicker(): Unit = {
      val tickIntervalMillis = registryConfiguration.metricTickInterval.toMillis
      val currentMetricTicker = metricReporterTickerSchedule.get()

      if(currentMetricTicker != null)
        currentMetricTicker.cancel(false)

      metricReporterTickerSchedule.set {
        val initialDelay =
          if(registryConfiguration.optimisticMetricTickAlignment) {
            val now = clock.instant()
            val nextTick = Clock.nextTick(now, registryConfiguration.metricTickInterval)
            Duration.between(now, nextTick).toMillis

          } else tickIntervalMillis

        registryExecutionContext.scheduleAtFixedRate(
          new MetricReporterTicker(metrics, metricReporters, clock), initialDelay, tickIntervalMillis, TimeUnit.MILLISECONDS
        )
      }
    }

    private def reStartTraceTicker(): Unit = {
      val tickIntervalMillis = registryConfiguration.traceTickInterval.toMillis
      val currentSpanTicker = spanReporterTickerSchedule.get()
      if(currentSpanTicker  != null)
        currentSpanTicker.cancel(false)

      spanReporterTickerSchedule.set {
        registryExecutionContext.scheduleAtFixedRate(
          new SpanReporterTicker(spanReporters), tickIntervalMillis, tickIntervalMillis, TimeUnit.MILLISECONDS
        )
      }
    }

    def reportSpan(span: Span.FinishedSpan): Unit = {
      spanReporters.foreach { case (_, reporterEntry) =>
        if(reporterEntry.isActive)
          reporterEntry.buffer.offer(span)
      }
    }

    private def stopMetricReporter(entry: MetricReporterEntry): Future[Unit] = {
      entry.isActive = false

      Future {
        Try {
          entry.reporter.stop()
        }.failed.foreach { error =>
          logger.error(s"Metric reporter [${entry.name}] failed to stop.", error)
        }
      }(entry.executionContext).andThen {
        case _ => entry.executionContext.shutdown()
      }(ExecutionContext.fromExecutor(registryExecutionContext))
    }

    private def stopSpanReporter(entry: SpanReporterEntry): Future[Unit] = {
      entry.isActive = false

      Future {
        Try {
          entry.reporter.stop()
        }.failed.foreach { error =>
          logger.error(s"Span reporter [${entry.name}] failed to stop.", error)
        }
      }(entry.executionContext).andThen {
        case _ => entry.executionContext.shutdown()
      }(ExecutionContext.fromExecutor(registryExecutionContext))
    }

    private class MetricReporterEntry(
      @volatile var isActive: Boolean = true,
      val id: Long,
      val name: String,
      val reporter: MetricReporter,
      val filter: Option[String],
      val executionContext: ExecutionContextExecutorService
    )

    private class SpanReporterEntry(
      @volatile var isActive: Boolean = true,
      val id: Long,
      val name: String,
      val reporter: SpanReporter,
      val bufferCapacity: Int,
      val executionContext: ExecutionContextExecutorService
    ) {
      val buffer = new ArrayBlockingQueue[Span.FinishedSpan](bufferCapacity)
    }

    private class MetricReporterTicker(snapshotGenerator: MetricsSnapshotGenerator, reporterEntries: TrieMap[Long, MetricReporterEntry],
        clock: Clock) extends Runnable {

      val logger = LoggerFactory.getLogger(classOf[MetricReporterTicker])
      var lastInstant = Instant.now(clock)

      def run(): Unit = try {
        val currentInstant = Instant.now(clock)
        val periodSnapshot = PeriodSnapshot(
          from = lastInstant,
          to = currentInstant,
          metrics = snapshotGenerator.snapshot()
        )

        reporterEntries.foreach { case (_, entry) =>
          Future {
            Try {
              if (entry.isActive) {
                val filteredSnapshot = entry.filter
                  .map(f => filterMetrics(f, periodSnapshot))
                  .getOrElse(periodSnapshot)

                entry.reporter.reportPeriodSnapshot(filteredSnapshot)
              }

            }.failed.foreach { error =>
              logger.error(s"Reporter [${entry.name}] failed to process a metrics tick.", error)
            }

          }(entry.executionContext)
        }

        lastInstant = currentInstant

      } catch {
        case NonFatal(t) => logger.error("Error while running a tick", t)
      }

      private def filterMetrics(filterName: String, periodSnapshot: PeriodSnapshot): PeriodSnapshot = {
        val metricFilter = Kamon.filter(filterName)
        val counters = periodSnapshot.metrics.counters.filter(c => metricFilter.accept(c.name))
        val gauges = periodSnapshot.metrics.gauges.filter(g => metricFilter.accept(g.name))
        val histograms = periodSnapshot.metrics.histograms.filter(h => metricFilter.accept(h.name))
        val rangeSamplers = periodSnapshot.metrics.rangeSamplers.filter(rs => metricFilter.accept(rs.name))

        periodSnapshot.copy(metrics = MetricsSnapshot(
          histograms, rangeSamplers, gauges, counters
        ))
      }
    }


    private class SpanReporterTicker(spanReporters: TrieMap[Long, SpanReporterEntry]) extends Runnable {
      override def run(): Unit = {
        spanReporters.foreach {
          case (_, entry) =>

            val spanBatch = new java.util.ArrayList[Span.FinishedSpan](entry.bufferCapacity)
            entry.buffer.drainTo(spanBatch, entry.bufferCapacity)

            Future {
              Try {
                entry.reporter.reportSpans(spanBatch.asScala.toSeq)
              }.failed.foreach { error =>
                logger.error(s"Reporter [${entry.name}] failed to report spans.", error)
              }
            }(entry.executionContext)
        }
      }
    }

    private def readRegistryConfiguration(config: Config): Configuration =
      Configuration(
        metricTickInterval = config.getDuration("kamon.metric.tick-interval"),
        optimisticMetricTickAlignment = config.getBoolean("kamon.metric.optimistic-tick-alignment"),
        traceTickInterval = config.getDuration("kamon.trace.tick-interval"),
        traceReporterQueueSize = config.getInt("kamon.trace.reporter-queue-size"),
        configuredReporters = config.getStringList("kamon.reporters").asScala.toSeq
      )

    private case class Configuration(metricTickInterval: Duration, optimisticMetricTickAlignment: Boolean,
      traceTickInterval: Duration, traceReporterQueueSize: Int, configuredReporters: Seq[String])
  }
}

