package kamon
package metric

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.Config
import kamon.metric.instrument.InstrumentFactory

import scala.collection.concurrent.TrieMap


trait RecorderRegistry {
  def getRecorder(entity: Entity): EntityRecorder
  def getRecorder(name: String, category: String, tags: Map[String, String]): EntityRecorder

  def removeRecorder(entity: Entity): Boolean
  def removeRecorder(name: String, category: String, tags: Map[String, String]): Boolean
}

class RecorderRegistryImpl(initialConfig: Config) extends RecorderRegistry {
  private val instrumentFactory = new AtomicReference[InstrumentFactory]()
  private val entities = TrieMap.empty[Entity, EntityRecorder with EntitySnapshotProducer]

  reconfigure(initialConfig)

  override def getRecorder(entity: Entity): EntityRecorder = {
    entities.atomicGetOrElseUpdate(entity, new DefaultEntityRecorder(entity, instrumentFactory.get()))
  }

  override def getRecorder(name: String, category: String, tags: Map[String, String]): EntityRecorder = ???

  override def removeRecorder(entity: Entity): Boolean = ???

  override def removeRecorder(name: String, category: String, tags: Map[String, String]): Boolean = ???

  private[kamon] def reconfigure(config: Config): Unit = {
    instrumentFactory.set(InstrumentFactory(config.getConfig("kamon.metric.instrument-factory")))
  }

  private[kamon] def snapshot(): Seq[EntitySnapshot] = {
    entities.values.map(_.snapshot()).toSeq
  }
}









