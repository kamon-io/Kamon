package kamon

import java.time.Instant
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, ThreadFactory, TimeUnit}

import com.typesafe.config.Config
import kamon.metric._
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait Reporters {
  def loadFromConfig(): Unit
  def stop(): Unit

  def addReporter(subscriber: MetricsReporter): Cancellable
  def addReporter(subscriber: MetricsReporter, name: String): Cancellable

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

class ReportersRegistry(metrics: RecorderRegistryImpl) extends Reporters {
  private val scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory("kamon-scheduler"))
  private val metricReporters = new ConcurrentLinkedQueue[ReporterEntry]()
  private val mReporters = TrieMap.empty[String, MetricsReporter]



  metricReporters.add(ReporterEntry(new DummyReporter("statsd"), createExecutionContext("statsd-reporter")))
  startMetricsTicker()




  override def loadFromConfig(): Unit = ???
  override def stop(): Unit = ???


  override def addReporter(subscriber: MetricsReporter): Cancellable = ???

  override def addReporter(subscriber: MetricsReporter, name: String): Cancellable = {
    ???
  }



  private def createExecutionContext(name: String): ExecutionContext = {
    val threadFactory = new ThreadFactory {
      val defaultFactory = Executors.defaultThreadFactory()
      override def newThread(r: Runnable): Thread = {
        val thread = defaultFactory.newThread(r)
        thread.setName(name)
        thread
      }
    }

    ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor(threadFactory))
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


  def reconfigure(config: Config): Unit = {}



  private case class ReporterEntry(reporter: MetricsReporter, executionContext: ExecutionContext)



  def startMetricsTicker(): Unit = {
    scheduler.scheduleAtFixedRate(new MetricTicker(metrics, metricReporters), 2, 2, TimeUnit.SECONDS)
  }


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
        Future(entry.reporter.processTick(tickSnapshot))(executor = entry.executionContext)
      }

      lastTick = currentTick

    } catch {
      case NonFatal(t) => logger.error("Error while running a tick", t)
    }
  }
}



trait Cancellable {
  def cancel(): Unit
}

trait MetricsReporter {
  def reconfigure(config: Config): Unit

  def start(config: Config): Unit
  def stop(): Unit

  def processTick(snapshot: TickSnapshot)
}



object TestingAllExample extends App {
  val recorder = Kamon.metrics.getRecorder(Entity("topo", "human-being", Map.empty))
  while(true) {
    recorder.counter("test-other").increment()
    Thread.sleep(100)
  }

}