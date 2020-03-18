package kamon.instrumentation.akka

import java.time.Duration

import com.typesafe.config.Config
import kamon.Kamon


object AkkaRemoteInstrumentation {

  @volatile private var _settings = readSettings(Kamon.config())
  Kamon.onReconfigure(newConfig => _settings = readSettings(newConfig))

  def settings(): Settings =
    _settings

  private def readSettings(config: Config): Settings =
    Settings(
      config.getBoolean("kamon.instrumentation.akka.remote.track-serialization-metrics"),
      config.getDuration("kamon.instrumentation.akka.cluster-sharding.shard-metrics-sample-interval")
    )

  case class Settings(
    trackSerializationMetrics: Boolean,
    shardMetricsSampleInterval: Duration
  )
}
