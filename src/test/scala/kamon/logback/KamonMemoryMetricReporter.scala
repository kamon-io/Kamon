package kamon.logback

import com.typesafe.config.Config
import kamon.MetricReporter
import kamon.metric.{MetricValue, PeriodSnapshot}

class KamonMemoryMetricReporter extends MetricReporter {

  def start(): Unit = Unit
  def stop(): Unit = Unit
  def reconfigure(config: Config): Unit = Unit

  @volatile var counters: List[MetricValue] = Nil

  def countersTotalByTag(counterName: String, tagName: String): Map[String, Long] =
    counters.collect{
      case MetricValue(`counterName`, tags, _, value) if tags.contains(tagName) ⇒
        (tags(tagName), value)
    }.groupBy(_._1).mapValues(_.map(_._2).sum)

  def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit =
    counters ++= snapshot.metrics.counters


}
