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

import com.typesafe.config.Config
import kamon.metric.SubscriptionsDispatcher.{ Unsubscribe, Subscribe }
import kamon.metric.instrument.{ DefaultRefreshScheduler, InstrumentFactory, CollectionContext }

import scala.collection.concurrent.TrieMap
import akka.actor._
import kamon.util.{ LazyActorRef, TriemapAtomicGetOrElseUpdate }

case class EntityRegistration[T <: EntityRecorder](entity: Entity, recorder: T)

trait Metrics {
  def settings: MetricsSettings
  def shouldTrack(entity: Entity): Boolean
  def shouldTrack(entityName: String, category: String): Boolean =
    shouldTrack(Entity(entityName, category))

  def register[T <: EntityRecorder](recorderFactory: EntityRecorderFactory[T], entityName: String): Option[EntityRegistration[T]]
  def register[T <: EntityRecorder](entity: Entity, recorder: T): EntityRegistration[T]
  def unregister(entity: Entity): Unit

  def find(entity: Entity): Option[EntityRecorder]
  def find(name: String, category: String): Option[EntityRecorder]

  def subscribe(filter: SubscriptionFilter, subscriber: ActorRef): Unit =
    subscribe(filter, subscriber, permanently = false)

  def subscribe(category: String, selection: String, subscriber: ActorRef, permanently: Boolean): Unit =
    subscribe(SubscriptionFilter(category, selection), subscriber, permanently)

  def subscribe(category: String, selection: String, subscriber: ActorRef): Unit =
    subscribe(SubscriptionFilter(category, selection), subscriber, permanently = false)

  def subscribe(filter: SubscriptionFilter, subscriber: ActorRef, permanently: Boolean): Unit

  def unsubscribe(subscriber: ActorRef): Unit
  def buildDefaultCollectionContext: CollectionContext
  def instrumentFactory(category: String): InstrumentFactory
}

private[kamon] class MetricsImpl(config: Config) extends Metrics {
  private val _trackedEntities = TrieMap.empty[Entity, EntityRecorder]
  private val _subscriptions = new LazyActorRef

  val settings = MetricsSettings(config)

  def shouldTrack(entity: Entity): Boolean =
    settings.entityFilters.get(entity.category).map {
      filter ⇒ filter.accept(entity.name)

    } getOrElse (settings.trackUnmatchedEntities)

  def register[T <: EntityRecorder](recorderFactory: EntityRecorderFactory[T], entityName: String): Option[EntityRegistration[T]] = {
    import TriemapAtomicGetOrElseUpdate.Syntax
    val entity = Entity(entityName, recorderFactory.category)

    if (shouldTrack(entity)) {
      val instrumentFactory = settings.instrumentFactories.get(recorderFactory.category).getOrElse(settings.defaultInstrumentFactory)
      val recorder = _trackedEntities.atomicGetOrElseUpdate(entity, recorderFactory.createRecorder(instrumentFactory), _.cleanup).asInstanceOf[T]

      Some(EntityRegistration(entity, recorder))
    } else None
  }

  def register[T <: EntityRecorder](entity: Entity, recorder: T): EntityRegistration[T] = {
    _trackedEntities.put(entity, recorder).map { oldRecorder ⇒
      oldRecorder.cleanup
    }

    EntityRegistration(entity, recorder)
  }

  def unregister(entity: Entity): Unit =
    _trackedEntities.remove(entity).map(_.cleanup)

  def find(entity: Entity): Option[EntityRecorder] =
    _trackedEntities.get(entity)

  def find(name: String, category: String): Option[EntityRecorder] =
    find(Entity(name, category))

  def subscribe(filter: SubscriptionFilter, subscriber: ActorRef, permanent: Boolean): Unit =
    _subscriptions.tell(Subscribe(filter, subscriber, permanent))

  def unsubscribe(subscriber: ActorRef): Unit =
    _subscriptions.tell(Unsubscribe(subscriber))

  def buildDefaultCollectionContext: CollectionContext =
    CollectionContext(settings.defaultCollectionContextBufferSize)

  def instrumentFactory(category: String): InstrumentFactory =
    settings.instrumentFactories.getOrElse(category, settings.defaultInstrumentFactory)

  private[kamon] def collectSnapshots(collectionContext: CollectionContext): Map[Entity, EntitySnapshot] = {
    val builder = Map.newBuilder[Entity, EntitySnapshot]
    _trackedEntities.foreach {
      case (identity, recorder) ⇒ builder += ((identity, recorder.collect(collectionContext)))
    }

    builder.result()
  }

  /**
   *  Metrics Extension initialization.
   */
  private var _system: ActorSystem = null
  private lazy val _start = {
    _subscriptions.point(_system.actorOf(SubscriptionsDispatcher.props(settings.tickInterval, this), "metrics"))
    settings.pointScheduler(DefaultRefreshScheduler(_system.scheduler, _system.dispatcher))
  }

  def start(system: ActorSystem): Unit = synchronized {
    _system = system
    _start
    _system = null
  }
}

private[kamon] object MetricsImpl {

  def apply(config: Config) =
    new MetricsImpl(config)
}

