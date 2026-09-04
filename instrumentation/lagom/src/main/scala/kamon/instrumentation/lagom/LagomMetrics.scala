package kamon.instrumentation.lagom

import kamon.Kamon
import kamon.metric.{Counter, Gauge, Histogram, InstrumentGroup, MeasurementUnit, Metric, Timer}
import kamon.tag.TagSet

object LagomMetrics {

  val defaultTags: TagSet = TagSet.of("component", "lagom")

  val CBState: Metric.Gauge = Kamon.gauge(
    name = "lagom.cb.state",
    description = "State of Lagom Circuit Breakers (possible values: 1 - Open, 2 - HalfOpen, 3 - Closed)"
  )

  val CBCallDuration: Metric.Timer = Kamon.timer(
    name = "lagom.cb.call.duration",
    description = "Call duration timer of Lagom Circuit Breakers"
  )

  class CircuitBreakerInstruments(circuitBreaker: String, tags: TagSet)
      extends InstrumentGroup(tags.withTag("cb.name", circuitBreaker)) {

    val state: Gauge = register(CBState)
    val okTimer: Timer = register(CBCallDuration, "status_code", "Ok")
    val errorTagSet: TagSet = TagSet.of("status_code", "Error")
    val errorTimer: Timer = register(CBCallDuration, errorTagSet)
    val openTimer: Timer =
      register(CBCallDuration, errorTagSet.withTag("exception.type", "CircuitBreakerOpenException"))
    val timeoutTimer: Timer = register(CBCallDuration, errorTagSet.withTag("exception.type", "TimeoutException"))
  }

}
