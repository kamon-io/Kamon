package kamon

import java.time.Instant
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import java.util.concurrent._

import com.typesafe.config.Config
import kamon.metric._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import scala.util.Try
import scala.util.control.NonFatal

trait ReporterRegistry {
  def loadFromConfig(): Unit
  def add(reporter: MetricsReporter): Registration
  def add(reporter: MetricsReporter, name: String): Registration
  def stopAll(): Future[Unit]
}

class ReporterRegistryImpl(metrics: RecorderRegistryImpl, initialConfig: Config) extends ReporterRegistry {
  private val registryExecutionContext = Executors.newSingleThreadScheduledExecutor(threadFactory("kamon-reporter-registry"))
  private val metricsTickerSchedule = new AtomicReference[ScheduledFuture[_]]()
  private val metricReporters = new ConcurrentLinkedQueue[ReporterEntry]()
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

  private def stopReporter(entry: ReporterEntry): Future[Unit] = {
    entry.isActive = false

    Future(entry.reporter.stop())(entry.executionContext).andThen {
      case _ => entry.executionContext.shutdown()
    }(ExecutionContext.fromExecutor(registryExecutionContext))
  }

  /**
    * Creates a thread factory that assigns the specified name to all created Threads.
    */
  private def threadFactory(name: String): ThreadFactory =
    new ThreadFactory {
      val defaultFactory = Executors.defaultThreadFactory()

      override def newThread(r: Runnable): Thread = {
        val thread = defaultFactory.newThread(r)
        thread.setName(name)
        thread
      }
    }


  private case class ReporterEntry(
    @volatile var isActive: Boolean = true,
    id: Long,
    reporter: MetricsReporter,
    executionContext: ExecutionContextExecutorService
  )

  private class MetricTicker(metricsImpl: RecorderRegistryImpl, reporterEntries: java.util.Queue[ReporterEntry]) extends Runnable {
    val logger = LoggerFactory.getLogger(classOf[MetricTicker])
    var lastTick = Instant.now()

    def run(): Unit = try {
      val currentTick = Instant.now()
      val tickSnapshot = TickSnapshot(
        interval = Interval(lastTick, currentTick),
        entities = metricsImpl.snapshot()
      )

      reporterEntries.forEach { entry =>
        Future {
          if(entry.isActive)
            entry.reporter.processTick(tickSnapshot)

        }(executor = entry.executionContext)
      }

      lastTick = currentTick

    } catch {
      case NonFatal(t) => logger.error("Error while running a tick", t)
    }
  }
}



trait Registration {
  def cancel(): Boolean
}

trait MetricsReporter {
  def reconfigure(config: Config): Unit

  def start(config: Config): Unit
  def stop(): Unit

  def processTick(snapshot: TickSnapshot)
}



object TestingAllExample extends App {
  val recorder = Kamon.metrics.getRecorder(Entity("topo", "human-being", Map.empty))

  val registration = Kamon.reporters.add(new DummyReporter("test"))

  var x = 0
  while(true) {
    recorder.counter("test-other").increment()
    Thread.sleep(100)
    x += 1

    if(x == 50) {
      registration.cancel()
    }

    if(x == 100) {
      println("Stopping all reporters")
      Kamon.reporters.stopAll()
    }
  }

}


class DummyReporter(name: String) extends MetricsReporter {
  override def reconfigure(config: Config): Unit = {
    println("NAME: " + name + "===> Reconfiguring Dummy")
  }

  override def start(config: Config): Unit = {

    println("NAME: " + name + "===> Starting DUMMY")
  }

  override def stop(): Unit = {
    println("NAME: " + name + "===> Stopping Dummy")
  }

  override def processTick(snapshot: TickSnapshot): Unit = {
    println("NAME: " + name + s"===> [${Thread.currentThread().getName()}] Processing a tick in dummy." + snapshot)
    println(s"From: ${snapshot.interval.from}, to: ${snapshot.interval.to}")
    snapshot.entities.foreach { e =>
      println(e.counters.map(c => s"Counter [${c.name}] => " + c.value).mkString(", "))
    }
  }
}