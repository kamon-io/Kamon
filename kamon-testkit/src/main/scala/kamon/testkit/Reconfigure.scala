package kamon.testkit

import com.typesafe.config.ConfigFactory
import kamon.Kamon

trait Reconfigure {

  def enableFastSpanFlushing(): Unit = {
    applyConfig("kamon.trace.tick-interval = 1 millisecond")
  }

  def sampleAlways(): Unit = {
    applyConfig("kamon.trace.sampler = always")
  }

  def sampleNever(): Unit = {
    applyConfig("kamon.trace.sampler = never")
  }

  def enableSpanMetricScoping(): Unit = {
    applyConfig("kamon.trace.span-metrics.scope-spans-to-parent = yes")
  }

  def disableSpanMetricScoping(): Unit = {
    applyConfig("kamon.trace.span-metrics.scope-spans-to-parent = no")
  }

  private def applyConfig(configString: String): Unit = {
    Kamon.reconfigure(ConfigFactory.parseString(configString).withFallback(Kamon.config()))
  }



}
