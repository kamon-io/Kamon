package kamon

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import java.util.concurrent._

import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import kamon.metric._
import kamon.trace.Span

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import scala.util.Try
import scala.util.control.NonFatal
import scala.collection.JavaConverters._

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
  def start(): Unit
  def stop(): Unit

  def reconfigure(config: Config): Unit
  def reportTickSnapshot(snapshot: TickSnapshot): Unit
}

trait SpansReporter {
  def start(): Unit
  def stop(): Unit

  def reconfigure(config: Config): Unit
  def reportSpan(span: Span.CompletedSpan): Unit
}

class ReporterRegistryImpl(metrics: MetricsSnapshotGenerator, initialConfig: Config) extends ReporterRegistry {
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
    val reporterEntry = new ReporterEntry(
      id = reporterCounter.getAndIncrement(),
      reporter = reporter,
      executionContext = ExecutionContext.fromExecutorService(executor)
    )

    metricReporters.add(reporterEntry)

    new Registration {
      val reporterID = reporterEntry.id
      override def cancel(): Boolean = {
        metricReporters.iterator().asScala
          .find(e => e.id == reporterID)
          .map(e => stopReporter(e))
          .isDefined
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

    Future.sequence(reporterStopFutures.result()).map(_ => Try((): Unit))
  }

  private[kamon] def reconfigure(config: Config): Unit = synchronized {
    val tickIntervalMillis = config.getDuration("kamon.metric.tick-interval", TimeUnit.MILLISECONDS)
    val currentTicker = metricsTickerSchedule.get()
    if(currentTicker != null) {
      currentTicker.cancel(true)
    }

    // Reconfigure all registered reporters
    metricReporters.iterator().asScala.foreach(entry =>
      Future(entry.reporter.reconfigure(config))(entry.executionContext)
    )

    metricsTickerSchedule.set {
      registryExecutionContext.scheduleAtFixedRate(
        new MetricTicker(metrics, metricReporters), tickIntervalMillis, tickIntervalMillis, TimeUnit.MILLISECONDS
      )
    }
  }


  private[kamon] def reportSpan(span: Span.CompletedSpan): Unit = {
    spanReporters.iterator().asScala.foreach(_.reportSpan(span))
  }

  private def stopReporter(entry: ReporterEntry): Future[Unit] = {
    entry.isActive = false

    Future(entry.reporter.stop())(entry.executionContext).andThen {
      case _ => entry.executionContext.shutdown()
    }(ExecutionContext.fromExecutor(registryExecutionContext))
  }

  private class ReporterEntry(
    @volatile var isActive: Boolean = true,
    val id: Long,
    val reporter: MetricsReporter,
    val executionContext: ExecutionContextExecutorService
  )

  private class MetricTicker(snapshotGenerator: MetricsSnapshotGenerator, reporterEntries: java.util.Queue[ReporterEntry]) extends Runnable {
    val logger = Logger(classOf[MetricTicker])
    var lastTick = System.currentTimeMillis()

    def run(): Unit = try {
      val currentTick = System.currentTimeMillis()
      val tickSnapshot = TickSnapshot(
        interval = Interval(lastTick, currentTick),
        metrics = snapshotGenerator.snapshot()
      )

      reporterEntries.iterator().asScala.foreach { entry =>
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