package kamon.metric

import java.util.concurrent.{ThreadPoolExecutor, ExecutorService}
import scala.concurrent.forkjoin.ForkJoinPool
import com.codahale.metrics.{Metric, MetricFilter}

object ExecutorServiceMetricCollector extends ForkJoinPoolMetricCollector with ThreadPoolExecutorMetricCollector {

  def register(fullName: String, executorService: ExecutorService) = executorService match {
    case fjp: ForkJoinPool => registerForkJoinPool(fullName, fjp)
    case tpe: ThreadPoolExecutor => registerThreadPoolExecutor(fullName, tpe)
    case _ => // If it is a unknown Executor then just do nothing.
  }

  def deregister(fullName: String) = {
    Metrics.registry.removeMatching(new MetricFilter {
      def matches(name: String, metric: Metric): Boolean = name.startsWith(fullName)
    })
  }
}


trait ForkJoinPoolMetricCollector {
  import GaugeGenerator._
  import BasicExecutorMetricNames._


  def registerForkJoinPool(fullName: String, fjp: ForkJoinPool) = {
    val forkJoinPoolGauge = newNumericGaugeFor(fjp) _

    val allMetrics = Map(
      fullName + queueSize            -> forkJoinPoolGauge(_.getQueuedTaskCount.toInt),
      fullName + poolSize             -> forkJoinPoolGauge(_.getPoolSize),
      fullName + activeThreads        -> forkJoinPoolGauge(_.getActiveThreadCount)
    )

    allMetrics.foreach(kv => Metrics.registry.register(kv._1, kv._2))
  }
}

trait ThreadPoolExecutorMetricCollector {
  import GaugeGenerator._
  import BasicExecutorMetricNames._

  def registerThreadPoolExecutor(fullName: String, tpe: ThreadPoolExecutor) = {
    val tpeGauge = newNumericGaugeFor(tpe) _

    val allMetrics = Map(
      fullName + queueSize            -> tpeGauge(_.getQueue.size()),
      fullName + poolSize             -> tpeGauge(_.getPoolSize),
      fullName + activeThreads        -> tpeGauge(_.getActiveCount)
    )

    allMetrics.foreach(kv => Metrics.registry.register(kv._1, kv._2))
  }
}


object BasicExecutorMetricNames {
  val queueSize = "queueSize"
  val poolSize = "poolSize"
  val activeThreads = "activeThreads"
}




