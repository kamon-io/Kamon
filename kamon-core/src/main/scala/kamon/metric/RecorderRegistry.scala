package kamon
package metric

import com.typesafe.config.Config
import kamon.metric.instrument.InstrumentFactory

import scala.collection.concurrent.TrieMap


trait RecorderRegistry {
  def getRecorder(entity: Entity): EntityRecorder
  def getRecorder(name: String, category: String, tags: Map[String, String]): EntityRecorder

  def removeRecorder(entity: Entity): Boolean
  def removeRecorder(name: String, category: String, tags: Map[String, String]): Boolean
}

class RecorderRegistryImpl(config: Config) extends RecorderRegistry {
  private val instrumentFactory = InstrumentFactory(config.getConfig("kamon.metric.instrument-factory"))
  private val entities = TrieMap.empty[Entity, EntityRecorder with EntitySnapshotProducer]

  override def getRecorder(entity: Entity): EntityRecorder = {
    entities.atomicGetOrElseUpdate(entity, new DefaultEntityRecorder(entity, instrumentFactory))
  }

  override def getRecorder(name: String, category: String, tags: Map[String, String]): EntityRecorder = ???

  override def removeRecorder(entity: Entity): Boolean = ???

  override def removeRecorder(name: String, category: String, tags: Map[String, String]): Boolean = ???

  private[kamon] def snapshot(): Seq[EntitySnapshot] = {
    entities.values.map(_.snapshot()).toSeq
  }
}









