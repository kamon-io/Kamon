package kamon.instrumentation.logback

import kamon.Kamon

object LogbackMetrics {

  val LogEvents = Kamon.counter(
    name = "log.events",
    description = "Counts the number of log events per level"
  )
}
