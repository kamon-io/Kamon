package kamon.system

import java.nio.ByteBuffer

import com.typesafe.config.Config
import kamon.{Kamon, MetricReporter}
import kamon.metric.TickSnapshot

object TestApp extends App {
  Kamon.addReporter(new LogReporter)
  SystemMetrics.startCollecting()

  while(true) {
    val x = ByteBuffer.allocate(2000000)
    Thread.sleep(100)
  }

}

class LogReporter extends MetricReporter {
  override def reportTickSnapshot(snapshot: TickSnapshot): Unit = {
    println("=================================================================================================")

    val allMetrics = Vector.newBuilder[String]

    snapshot.metrics.gauges.foreach(m => allMetrics += ("Metric: " + m.name + " " + tagsToString(m.tags)))
    snapshot.metrics.histograms.foreach(m => allMetrics += ("Metric: " + m.name + " " + tagsToString(m.tags)))
    snapshot.metrics.counters.foreach(m => allMetrics += ("Metric: " + m.name + " " + tagsToString(m.tags)))
    snapshot.metrics.minMaxCounters.foreach(m => allMetrics += ("Metric: " + m.name + " " + tagsToString(m.tags)))

    allMetrics.result().sorted.foreach(println)
  }


  private def tagsToString(tags: Map[String, String]): String = {
    tags.map{ case (key, value) => key + "=" + value } mkString("{", ", ", "}")
  }

  override def start(): Unit = {}

  override def stop(): Unit = {}

  override def reconfigure(config: Config): Unit = {}
}
