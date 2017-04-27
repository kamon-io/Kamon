package kamon.testkit

import com.typesafe.config.ConfigFactory
import kamon.metric.Entity
import kamon.metric.instrument.InstrumentFactory

trait DefaultInstrumentFactory {
  val defaultEntity = Entity("default-entity", "default-category", Map.empty)
  val instrumentFactory = InstrumentFactory(ConfigFactory.load().getConfig("kamon.metric.instrument-factory"))

  def buildCounter(name: String) = instrumentFactory.buildCounter(defaultEntity, name)

}
