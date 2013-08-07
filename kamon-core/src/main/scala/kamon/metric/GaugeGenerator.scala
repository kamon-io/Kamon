package kamon.metric

import com.codahale.metrics.Gauge

trait GaugeGenerator {

  def newNumericGaugeFor[T, V >: AnyVal](target: T)(generator: T => V) = new Gauge[V] {
    def getValue: V = generator(target)
  }
}

object GaugeGenerator extends GaugeGenerator
