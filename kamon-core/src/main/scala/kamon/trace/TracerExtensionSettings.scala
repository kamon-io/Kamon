package kamon.trace

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem

case class TraceSettings(levelOfDetail: LevelOfDetail, sampler: Sampler)

object TraceSettings {
  def apply(system: ActorSystem): TraceSettings = {
    val tracerConfig = system.settings.config.getConfig("kamon.trace")

    val detailLevel: LevelOfDetail = tracerConfig.getString("level-of-detail") match {
      case "metrics-only" ⇒ LevelOfDetail.MetricsOnly
      case "simple-trace" ⇒ LevelOfDetail.SimpleTrace
      case other          ⇒ sys.error(s"Unknown tracer level of detail [$other] present in the configuration file.")
    }

    val sampler: Sampler =
      if (detailLevel == LevelOfDetail.MetricsOnly) NoSampling
      else tracerConfig.getString("sampling") match {
        case "all"       ⇒ SampleAll
        case "random"    ⇒ new RandomSampler(tracerConfig.getInt("random-sampler.chance"))
        case "ordered"   ⇒ new OrderedSampler(tracerConfig.getInt("ordered-sampler.interval"))
        case "threshold" ⇒ new ThresholdSampler(tracerConfig.getDuration("threshold-sampler.minimum-elapsed-time", TimeUnit.NANOSECONDS))
      }

    TraceSettings(detailLevel, sampler)
  }
}