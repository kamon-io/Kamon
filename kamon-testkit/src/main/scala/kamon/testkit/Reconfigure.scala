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

  private def applyConfig(configString: String): Unit = {
    Kamon.reconfigure(ConfigFactory.parseString(configString).withFallback(Kamon.config()))
  }



}
