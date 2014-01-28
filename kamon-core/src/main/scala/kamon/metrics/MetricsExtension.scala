/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.metrics

import scala.collection.concurrent.TrieMap
import akka.actor._
import com.typesafe.config.Config
import kamon.util.GlobPathFilter
import kamon.Kamon
import akka.actor
import kamon.metrics.Metrics.MetricGroupFilter
import kamon.metrics.MetricGroupIdentity.Category
import kamon.metrics.Metrics.MetricGroupFilter
import scala.Some
import kamon.metrics.Subscriptions.Subscribe

case class MetricGroupIdentity(name: String, category: MetricGroupIdentity.Category)

trait MetricIdentity {
  def name: String
}

trait MetricGroupRecorder {
  def record(identity: MetricIdentity, value: Long)
  def collect: MetricGroupSnapshot
}

trait MetricGroupSnapshot {
  def metrics: Map[MetricIdentity, MetricSnapshot]
}

trait MetricRecorder {
  def record(value: Long)
  def collect(): MetricSnapshot
}

trait MetricSnapshot {
  def numberOfMeasurements: Long
  def measurementLevels: Vector[MetricSnapshot.Measurement]
}

object MetricSnapshot {
  case class Measurement(value: Long, count: Long)
}

case class DefaultMetricSnapshot(numberOfMeasurements: Long, measurementLevels: Vector[MetricSnapshot.Measurement]) extends MetricSnapshot

object MetricGroupIdentity {
  trait Category {
    def name: String
  }

  val AnyCategory = new Category {
    def name: String = "match-all"
    override def equals(that: Any): Boolean = that.isInstanceOf[Category]
  }
}

trait MetricGroupFactory {
  type Group <: MetricGroupRecorder
  def create(config: Config): Group
}



class MetricsExtension(val system: ExtendedActorSystem) extends Kamon.Extension {
  val config = system.settings.config
  val storage = TrieMap[MetricGroupIdentity, MetricGroupRecorder]()
  val filters = loadFilters(config)
  lazy val subscriptions = system.actorOf(Props[Subscriptions], "kamon-metrics-subscriptions")

  def register(name: String, category: MetricGroupIdentity.Category with MetricGroupFactory): Option[category.Group] = {
    if (shouldTrack(name, category))
      Some(storage.getOrElseUpdate(MetricGroupIdentity(name, category), category.create(config)).asInstanceOf[category.Group])
    else
      None
  }

  def unregister(name: String, category: MetricGroupIdentity.Category with MetricGroupFactory): Unit = {
    storage.remove(MetricGroupIdentity(name, category))
  }

  def subscribe(category: Category, selection: String, receiver: ActorRef, permanently: Boolean = false): Unit = {
    subscriptions.tell(Subscribe(category, selection, permanently), receiver)
  }

  def collect: Map[MetricGroupIdentity, MetricGroupSnapshot] = {
    (for ((identity, recorder) ← storage) yield (identity, recorder.collect)).toMap
  }

  private def shouldTrack(name: String, category: MetricGroupIdentity.Category): Boolean = {
    filters.get(category.name).map(filter ⇒ filter.accept(name)).getOrElse(false)
  }

  def loadFilters(config: Config): Map[String, MetricGroupFilter] = {
    import scala.collection.JavaConverters._

    val filters = config.getObjectList("kamon.metrics.filters").asScala

    val allFilters =
      for (
        filter ← filters;
        entry ← filter.entrySet().asScala
      ) yield {
        val key = entry.getKey
        val keyBasedConfig = entry.getValue.atKey(key)

        val includes = keyBasedConfig.getStringList(s"$key.includes").asScala.map(inc ⇒ new GlobPathFilter(inc)).toList
        val excludes = keyBasedConfig.getStringList(s"$key.excludes").asScala.map(exc ⇒ new GlobPathFilter(exc)).toList

        (key, MetricGroupFilter(includes, excludes))
      }

    allFilters.toMap
  }

}

object Metrics extends ExtensionId[MetricsExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = Metrics
  def createExtension(system: ExtendedActorSystem): MetricsExtension = new MetricsExtension(system)

  case class MetricGroupFilter(includes: List[GlobPathFilter], excludes: List[GlobPathFilter]) {
    def accept(name: String): Boolean = includes.exists(_.accept(name)) && !excludes.exists(_.accept(name))
  }
}
