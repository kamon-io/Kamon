package kamon
package metric

import scala.collection.concurrent.TrieMap


trait Metrics {
  def getRecorder(entity: Entity): EntityRecorder
  def getRecorder(name: String, category: String, tags: Map[String, String]): EntityRecorder

  def removeRecorder(entity: Entity): Boolean
  def removeRecorder(name: String, category: String, tags: Map[String, String]): Boolean
}

class MetricsImpl extends Metrics{
  private val entities = TrieMap.empty[Entity, EntityRecorder]

  override def getRecorder(entity: Entity): EntityRecorder = {
    ???
  }

  override def getRecorder(name: String, category: String, tags: Map[String, String]): EntityRecorder = ???

  override def removeRecorder(entity: Entity): Boolean = ???

  override def removeRecorder(name: String, category: String, tags: Map[String, String]): Boolean = ???
}







