package kamon

import java.time.Instant
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import java.util.concurrent._

import com.typesafe.config.Config
import kamon.metric._
import kamon.trace.Span
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import scala.util.Try
import scala.util.control.NonFatal

trait ReporterRegistry {
  def loadFromConfig(): Unit

  def add(reporter: MetricsReporter): Registration
  def add(reporter: MetricsReporter, name: String): Registration
  def add(reporter: SpansReporter): Registration

  def stopAll(): Future[Unit]
}


trait Registration {
  def cancel(): Boolean
}

trait MetricsReporter {
  def start(config: Config): Unit
  def reconfigure(config: Config): Unit
  def stop(): Unit

  def reportTickSnapshot(snapshot: TickSnapshot)
}

trait SpansReporter {
  def start(config: Config): Unit
  def reconfigure(config: Config): Unit
  def stop(): Unit

  def reportSpan(span: Span.CompletedSpan): Unit
}

class ReporterRegistryImpl(metrics: RegistrySnapshotGenerator, initialConfig: Config) extends ReporterRegistry {
  private val registryExecutionContext = Executors.newSingleThreadScheduledExecutor(threadFactory("kamon-reporter-registry"))
  private val metricsTickerSchedule = new AtomicReference[ScheduledFuture[_]]()
  private val metricReporters = new ConcurrentLinkedQueue[ReporterEntry]()
  private val spanReporters = new ConcurrentLinkedQueue[SpansReporter]()
  private val reporterCounter = new AtomicLong(0L)

  reconfigure(initialConfig)

  override def loadFromConfig(): Unit = ???

  override def add(reporter: MetricsReporter): Registration =
    add(reporter, reporter.getClass.getName())

  override def add(reporter: MetricsReporter, name: String): Registration = {
    val executor = Executors.newSingleThreadExecutor(threadFactory(name))
    val reporterEntry = ReporterEntry(
      id = reporterCounter.getAndIncrement(),
      reporter = reporter,
      executionContext = ExecutionContext.fromExecutorService(executor)
    )

    metricReporters.add(reporterEntry)

    new Registration {
      val reporterID = reporterEntry.id
      override def cancel(): Boolean = {
        metricReporters.removeIf(entry => {
          if(entry.id == reporterID) {
            stopReporter(entry)
            true
          } else false
        })
      }
    }
  }

  override def add(reporter: SpansReporter): Registration = {
    spanReporters.add(reporter)

    new Registration {
      override def cancel(): Boolean = true
    }
  }

  override def stopAll(): Future[Unit] = {
    implicit val stopReporterExeContext = ExecutionContext.fromExecutor(registryExecutionContext)
    val reporterStopFutures = Vector.newBuilder[Future[Unit]]
    while(!metricReporters.isEmpty) {
      val entry = metricReporters.poll()
      if(entry != null) {
        reporterStopFutures += stopReporter(entry)
      }
    }

    Future.sequence(reporterStopFutures.result()).transform(_ => Try((): Unit))
  }

  private[kamon] def reconfigure(config: Config): Unit = synchronized {
    val tickInterval = config.getDuration("kamon.metric.tick-interval")
    val currentTicker = metricsTickerSchedule.get()
    if(currentTicker != null) {
      currentTicker.cancel(true)
    }

    // Reconfigure all registered reporters
    metricReporters.forEach(entry =>
      Future(entry.reporter.reconfigure(config))(entry.executionContext)
    )

    metricsTickerSchedule.set {
      registryExecutionContext.scheduleAtFixedRate(
        new MetricTicker(metrics, metricReporters), tickInterval.toMillis, tickInterval.toMillis, TimeUnit.MILLISECONDS
      )
    }
  }


  private[kamon] def reportSpan(span: Span.CompletedSpan): Unit = {
    spanReporters.forEach(_.reportSpan(span))
  }

  private def stopReporter(entry: ReporterEntry): Future[Unit] = {
    entry.isActive = false

    Future(entry.reporter.stop())(entry.executionContext).andThen {
      case _ => entry.executionContext.shutdown()
    }(ExecutionContext.fromExecutor(registryExecutionContext))
  }

  private case class ReporterEntry(
    @volatile var isActive: Boolean = true,
    id: Long,
    reporter: MetricsReporter,
    executionContext: ExecutionContextExecutorService
  )

  private class MetricTicker(snapshotGenerator: RegistrySnapshotGenerator, reporterEntries: java.util.Queue[ReporterEntry]) extends Runnable {
    val logger = LoggerFactory.getLogger(classOf[MetricTicker])
    var lastTick = Instant.now()

    def run(): Unit = try {
      val currentTick = Instant.now()
      val tickSnapshot = TickSnapshot(
        interval = Interval(lastTick, currentTick),
        metrics = snapshotGenerator.snapshot()
      )

      reporterEntries.forEach { entry =>
        Future {
          if(entry.isActive)
            entry.reporter.reportTickSnapshot(tickSnapshot)

        }(executor = entry.executionContext)
      }

      lastTick = currentTick

    } catch {
      case NonFatal(t) => logger.error("Error while running a tick", t)
    }
  }
}