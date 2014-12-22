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

package kamon.metric

import akka.event.Logging.Error
import akka.event.EventStream

import scala.collection.concurrent.TrieMap
import akka.actor._
import com.typesafe.config.Config
import kamon.util.GlobPathFilter
import kamon.Kamon
import akka.actor
import kamon.metric.Metrics.MetricGroupFilter
import kamon.metric.Subscriptions.{ Unsubscribe, Subscribe }
import java.util.concurrent.TimeUnit

class MetricsExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  import Metrics.AtomicGetOrElseUpdateForTriemap

  val metricsExtConfig = system.settings.config.getConfig("kamon.metrics")
  printInitializationMessage(system.eventStream, metricsExtConfig.getBoolean("disable-aspectj-weaver-missing-error"))

  /** Configured Dispatchers */
  val metricSubscriptionsDispatcher = system.dispatchers.lookup(metricsExtConfig.getString("dispatchers.metric-subscriptions"))
  val gaugeRecordingsDispatcher = system.dispatchers.lookup(metricsExtConfig.getString("dispatchers.gauge-recordings"))

  /** Configuration Settings */
  val gaugeRecordingInterval = metricsExtConfig.getDuration("gauge-recording-interval", TimeUnit.MILLISECONDS)

  val storage = TrieMap[MetricGroupIdentity, MetricGroupRecorder]()
  val filters = loadFilters(metricsExtConfig)
  lazy val subscriptions = system.actorOf(Props[Subscriptions], "kamon-metrics-subscriptions")

  def register(identity: MetricGroupIdentity, factory: MetricGroupFactory): Option[factory.GroupRecorder] = {
    if (shouldTrack(identity))
      Some(storage.atomicGetOrElseUpdate(identity, factory.create(metricsExtConfig, system)).asInstanceOf[factory.GroupRecorder])
    else
      None
  }

  def unregister(identity: MetricGroupIdentity): Unit = {
    storage.remove(identity).map(_.cleanup)
  }

  def subscribe[C <: MetricGroupCategory](category: C, selection: String, subscriber: ActorRef, permanently: Boolean = false): Unit =
    subscriptions.tell(Subscribe(category, selection, subscriber, permanently), subscriber)

  def unsubscribe(subscriber: ActorRef): Unit =
    subscriptions.tell(Unsubscribe(subscriber), subscriber)

  def scheduleGaugeRecorder(body: ⇒ Unit): Cancellable = {
    import scala.concurrent.duration._

    system.scheduler.schedule(gaugeRecordingInterval milliseconds, gaugeRecordingInterval milliseconds) {
      body
    }(gaugeRecordingsDispatcher)
  }

  private def shouldTrack(identity: MetricGroupIdentity): Boolean = {
    filters.get(identity.category.name).map(filter ⇒ filter.accept(identity.name)).getOrElse(true)
  }

  def loadFilters(config: Config): Map[String, MetricGroupFilter] = {
    import scala.collection.JavaConverters._

    val filters = config.getObjectList("filters").asScala

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

  def buildDefaultCollectionContext: CollectionContext =
    CollectionContext(metricsExtConfig.getInt("default-collection-context-buffer-size"))

  def printInitializationMessage(eventStream: EventStream, disableWeaverMissingError: Boolean): Unit = {
    if (!disableWeaverMissingError) {
      val weaverMissingMessage =
        """
          |
          |  ___                           _      ___   _    _                                 ___  ___ _            _
          | / _ \                         | |    |_  | | |  | |                                |  \/  |(_)          (_)
          |/ /_\ \ ___  _ __    ___   ___ | |_     | | | |  | |  ___   __ _ __   __ ___  _ __  | .  . | _  ___  ___  _  _ __    __ _
          ||  _  |/ __|| '_ \  / _ \ / __|| __|    | | | |/\| | / _ \ / _` |\ \ / // _ \| '__| | |\/| || |/ __|/ __|| || '_ \  / _` |
          || | | |\__ \| |_) ||  __/| (__ | |_ /\__/ / \  /\  /|  __/| (_| | \ V /|  __/| |    | |  | || |\__ \\__ \| || | | || (_| |
          |\_| |_/|___/| .__/  \___| \___| \__|\____/   \/  \/  \___| \__,_|  \_/  \___||_|    \_|  |_/|_||___/|___/|_||_| |_| \__, |
          |            | |                                                                                                      __/ |
          |            |_|                                                                                                     |___/
          |
          | It seems like your application wasn't started with the -javaagent:/path-to-aspectj-weaver.jar option. Without that Kamon might
          | not work properly, if you need help on setting up the weaver go to http://kamon.io/introduction/get-started/ for more info. If
          | you are sure that you don't need the weaver (e.g. you are only using KamonStandalone) then you can disable this error message
          | by changing the kamon.metrics.disable-aspectj-weaver-missing-error setting in your configuration file.
          |
        """.stripMargin

      eventStream.publish(Error("MetricsExtension", classOf[MetricsExtension], weaverMissingMessage))
    }
  }
}

object Metrics extends ExtensionId[MetricsExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = Metrics
  def createExtension(system: ExtendedActorSystem): MetricsExtension = new MetricsExtension(system)

  case class MetricGroupFilter(includes: List[GlobPathFilter], excludes: List[GlobPathFilter]) {
    def accept(name: String): Boolean = includes.exists(_.accept(name)) && !excludes.exists(_.accept(name))
  }

  implicit class AtomicGetOrElseUpdateForTriemap[K, V](trieMap: TrieMap[K, V]) {
    def atomicGetOrElseUpdate(key: K, op: ⇒ V): V =
      trieMap.get(key) match {
        case Some(v) ⇒ v
        case None    ⇒ val d = op; trieMap.putIfAbsent(key, d).getOrElse(d)
      }
  }
}
