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
import scala.collection.concurrent.TrieMap

trait ReporterRegistry {
  def loadFromConfig(): Unit

  def add(reporter: MetricReporter): Registration
  def add(reporter: MetricReporter, name: String): Registration
  def add(reporter: SpanReporter): Registration
  def add(reporter: SpanReporter, name: String): Registration

  def stopAll(): Future[Unit]
}


trait Registration {
  def cancel(): Boolean
}

trait MetricReporter {
  def start(): Unit
  def stop(): Unit

  def reconfigure(config: Config): Unit
  def reportTickSnapshot(snapshot: TickSnapshot): Unit
}

trait SpanReporter {
  def start(): Unit
  def stop(): Unit

  def reconfigure(config: Config): Unit
  def reportSpans(spans: Seq[Span.CompletedSpan]): Unit
}

class ReporterRegistryImpl(metrics: MetricsSnapshotGenerator, initialConfig: Config) extends ReporterRegistry {
  private val reporterCounter = new AtomicLong(0L)
  private val registryExecutionContext = Executors.newSingleThreadScheduledExecutor(threadFactory("kamon-reporter-registry"))
  private val metricsTickerSchedule = new AtomicReference[ScheduledFuture[_]]()
  private val spanReporterTickerSchedule = new AtomicReference[ScheduledFuture[_]]()

  private val metricReporters = TrieMap[Long, MetricReporterEntry]()
  private val spanReporters = TrieMap[Long, SpanReporterEntry]()


  reconfigure(initialConfig)

  override def loadFromConfig(): Unit = ???

  override def add(reporter: MetricReporter): Registration =
    addMetricReporter(reporter, reporter.getClass.getName())

  override def add(reporter: MetricReporter, name: String): Registration =
    addMetricReporter(reporter, name)

  override def add(reporter: SpanReporter): Registration =
    addSpanReporter(reporter, reporter.getClass.getName())

  override def add(reporter: SpanReporter, name: String): Registration =
    addSpanReporter(reporter, name)


  private def addMetricReporter(reporter: MetricReporter, name: String): Registration = {
    val executor = Executors.newSingleThreadExecutor(threadFactory(name))
    val reporterEntry = new MetricReporterEntry(
      id = reporterCounter.getAndIncrement(),
      reporter = reporter,
      executionContext = ExecutionContext.fromExecutorService(executor)
    )

    metricReporters.put(reporterEntry.id, reporterEntry)
    createRegistration(reporterEntry.id, metricReporters)
  }

  private def addSpanReporter(reporter: SpanReporter, name: String): Registration = {
    val executor = Executors.newSingleThreadExecutor(threadFactory(name))
    val reporterEntry = new SpanReporterEntry(
      id = reporterCounter.incrementAndGet(),
      reporter = reporter,
      bufferCapacity = 1024,
      executionContext = ExecutionContext.fromExecutorService(executor)
    )

    spanReporters.put(reporterEntry.id, reporterEntry)
    createRegistration(reporterEntry.id, spanReporters)
  }

  private def createRegistration(id: Long, target: TrieMap[Long, _]): Registration = new Registration {
    override def cancel(): Boolean =
      metricReporters.remove(id).nonEmpty
  }

  override def stopAll(): Future[Unit] = {
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

    Future.sequence(reporterStopFutures.result()).map(_ => Try((): Unit))
  }

  private[kamon] def reconfigure(config: Config): Unit = synchronized {
    val tickIntervalMillis = config.getDuration("kamon.metric.tick-interval", TimeUnit.MILLISECONDS)
    val traceTickIntervalMillis = config.getDuration("kamon.trace.tick-interval", TimeUnit.MILLISECONDS)

    val currentTicker = metricsTickerSchedule.get()
    if(currentTicker != null) {
      currentTicker.cancel(true)
    }

    // Reconfigure all registered reporters
    metricReporters.foreach { case (_, entry) =>
      Future(entry.reporter.reconfigure(config))(entry.executionContext)
    }

    spanReporters.foreach { case (_, entry) =>
      Future(entry.reporter.reconfigure(config))(entry.executionContext)
    }

    metricsTickerSchedule.set {
      registryExecutionContext.scheduleAtFixedRate(
        new MetricTicker(metrics, metricReporters), tickIntervalMillis, tickIntervalMillis, TimeUnit.MILLISECONDS
      )
    }

    spanReporterTickerSchedule.set {
      registryExecutionContext.scheduleAtFixedRate(
        new SpanTicker(spanReporters), traceTickIntervalMillis, traceTickIntervalMillis, TimeUnit.MILLISECONDS
      )
    }
  }

  private[kamon] def reportSpan(span: Span.CompletedSpan): Unit = {
    spanReporters.foreach { case (_, reporterEntry) =>
      if(reporterEntry.isActive)
        reporterEntry.buffer.offer(span)
    }
  }

  private def stopMetricReporter(entry: MetricReporterEntry): Future[Unit] = {
    entry.isActive = false

    Future(entry.reporter.stop())(entry.executionContext).andThen {
      case _ => entry.executionContext.shutdown()
    }(ExecutionContext.fromExecutor(registryExecutionContext))
  }

  private def stopSpanReporter(entry: SpanReporterEntry): Future[Unit] = {
    entry.isActive = false

    Future(entry.reporter.stop())(entry.executionContext).andThen {
      case _ => entry.executionContext.shutdown()
    }(ExecutionContext.fromExecutor(registryExecutionContext))
  }

  private class MetricReporterEntry(
    @volatile var isActive: Boolean = true,
    val id: Long,
    val reporter: MetricReporter,
    val executionContext: ExecutionContextExecutorService
  )

  private class SpanReporterEntry(
    @volatile var isActive: Boolean = true,
    val id: Long,
    val reporter: SpanReporter,
    val bufferCapacity: Int,
    val executionContext: ExecutionContextExecutorService
  ) {
    val buffer = new ArrayBlockingQueue[Span.CompletedSpan](bufferCapacity)
  }

  private class MetricTicker(snapshotGenerator: MetricsSnapshotGenerator, reporterEntries: TrieMap[Long, MetricReporterEntry]) extends Runnable {
    val logger = Logger(classOf[MetricTicker])
    var lastTick = System.currentTimeMillis()

    def run(): Unit = try {
      val currentTick = System.currentTimeMillis()
      val tickSnapshot = TickSnapshot(
        interval = Interval(lastTick, currentTick),
        metrics = snapshotGenerator.snapshot()
      )

      reporterEntries.foreach { case (_, entry) =>
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

  private class SpanTicker(spanReporters: TrieMap[Long, SpanReporterEntry]) extends Runnable {
    override def run(): Unit = {
      spanReporters.foreach {
        case (_, entry) =>

          val spanBatch = new java.util.ArrayList[Span.CompletedSpan](entry.bufferCapacity)
          entry.buffer.drainTo(spanBatch, entry.bufferCapacity)

          Future {
            entry.reporter.reportSpans(spanBatch.asScala)
          }(entry.executionContext)
      }
    }
  }
}