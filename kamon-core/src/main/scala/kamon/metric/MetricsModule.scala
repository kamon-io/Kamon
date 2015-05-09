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

import akka.actor._
import com.typesafe.config.Config
import kamon.metric.SubscriptionsDispatcher.{ Subscribe, Unsubscribe }
import kamon.metric.instrument.Gauge.CurrentValueCollector
import kamon.metric.instrument.Histogram.DynamicRange
import kamon.metric.instrument._
import kamon.util.LazyActorRef

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration

case class EntityRegistration[T <: EntityRecorder](entity: Entity, recorder: T)

trait MetricsModule {
  def settings: MetricsSettings

  def shouldTrack(entity: Entity): Boolean

  def shouldTrack(entityName: String, category: String): Boolean =
    shouldTrack(Entity(entityName, category))

  //
  // Histograms registration and removal
  //

  def histogram(name: String): Histogram =
    registerHistogram(name)

  def histogram(name: String, unitOfMeasurement: UnitOfMeasurement): Histogram =
    registerHistogram(name, unitOfMeasurement = Some(unitOfMeasurement))

  def histogram(name: String, dynamicRange: DynamicRange): Histogram =
    registerHistogram(name, dynamicRange = Some(dynamicRange))

  def histogram(name: String, unitOfMeasurement: UnitOfMeasurement, dynamicRange: DynamicRange): Histogram =
    registerHistogram(name, unitOfMeasurement = Some(unitOfMeasurement), dynamicRange = Some(dynamicRange))

  def histogram(name: String, tags: Map[String, String]): Histogram =
    registerHistogram(name, tags)

  def histogram(name: String, tags: Map[String, String], unitOfMeasurement: UnitOfMeasurement): Histogram =
    registerHistogram(name, tags, Some(unitOfMeasurement))

  def histogram(name: String, tags: Map[String, String], dynamicRange: DynamicRange): Histogram =
    registerHistogram(name, tags, dynamicRange = Some(dynamicRange))

  def histogram(name: String, tags: Map[String, String], unitOfMeasurement: UnitOfMeasurement, dynamicRange: DynamicRange): Histogram =
    registerHistogram(name, tags, Some(unitOfMeasurement), Some(dynamicRange))

  def removeHistogram(name: String): Boolean =
    removeHistogram(name, Map.empty)

  def registerHistogram(name: String, tags: Map[String, String] = Map.empty, unitOfMeasurement: Option[UnitOfMeasurement] = None,
    dynamicRange: Option[DynamicRange] = None): Histogram

  def removeHistogram(name: String, tags: Map[String, String]): Boolean

  //
  // MinMaxCounter registration and removal
  //

  def minMaxCounter(name: String): MinMaxCounter =
    registerMinMaxCounter(name)

  def minMaxCounter(name: String, unitOfMeasurement: UnitOfMeasurement): MinMaxCounter =
    registerMinMaxCounter(name, unitOfMeasurement = Some(unitOfMeasurement))

  def minMaxCounter(name: String, dynamicRange: DynamicRange): MinMaxCounter =
    registerMinMaxCounter(name, dynamicRange = Some(dynamicRange))

  def minMaxCounter(name: String, refreshInterval: FiniteDuration): MinMaxCounter =
    registerMinMaxCounter(name, refreshInterval = Some(refreshInterval))

  def minMaxCounter(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration): MinMaxCounter =
    registerMinMaxCounter(name, dynamicRange = Some(dynamicRange), refreshInterval = Some(refreshInterval))

  def minMaxCounter(name: String, unitOfMeasurement: UnitOfMeasurement, dynamicRange: DynamicRange): MinMaxCounter =
    registerMinMaxCounter(name, unitOfMeasurement = Some(unitOfMeasurement), dynamicRange = Some(dynamicRange))

  def minMaxCounter(name: String, tags: Map[String, String]): MinMaxCounter =
    registerMinMaxCounter(name, tags)

  def minMaxCounter(name: String, tags: Map[String, String], unitOfMeasurement: UnitOfMeasurement): MinMaxCounter =
    registerMinMaxCounter(name, tags, Some(unitOfMeasurement))

  def minMaxCounter(name: String, tags: Map[String, String], dynamicRange: DynamicRange): MinMaxCounter =
    registerMinMaxCounter(name, tags, dynamicRange = Some(dynamicRange))

  def minMaxCounter(name: String, tags: Map[String, String], unitOfMeasurement: UnitOfMeasurement, dynamicRange: DynamicRange): MinMaxCounter =
    registerMinMaxCounter(name, tags, Some(unitOfMeasurement), Some(dynamicRange))

  def removeMinMaxCounter(name: String): Boolean =
    removeMinMaxCounter(name, Map.empty)

  def removeMinMaxCounter(name: String, tags: Map[String, String]): Boolean

  def registerMinMaxCounter(name: String, tags: Map[String, String] = Map.empty, unitOfMeasurement: Option[UnitOfMeasurement] = None,
    dynamicRange: Option[DynamicRange] = None, refreshInterval: Option[FiniteDuration] = None): MinMaxCounter

  //
  // Gauge registration and removal
  //

  def gauge(name: String)(valueCollector: CurrentValueCollector): Gauge =
    registerGauge(name, valueCollector)

  def gauge(name: String, unitOfMeasurement: UnitOfMeasurement)(valueCollector: CurrentValueCollector): Gauge =
    registerGauge(name, valueCollector, unitOfMeasurement = Some(unitOfMeasurement))

  def gauge(name: String, dynamicRange: DynamicRange)(valueCollector: CurrentValueCollector): Gauge =
    registerGauge(name, valueCollector, dynamicRange = Some(dynamicRange))

  def gauge(name: String, refreshInterval: FiniteDuration)(valueCollector: CurrentValueCollector): Gauge =
    registerGauge(name, valueCollector, refreshInterval = Some(refreshInterval))

  def gauge(name: String, dynamicRange: DynamicRange, refreshInterval: FiniteDuration)(valueCollector: CurrentValueCollector): Gauge =
    registerGauge(name, valueCollector, dynamicRange = Some(dynamicRange), refreshInterval = Some(refreshInterval))

  def gauge(name: String, unitOfMeasurement: UnitOfMeasurement, dynamicRange: DynamicRange)(valueCollector: CurrentValueCollector): Gauge =
    registerGauge(name, valueCollector, unitOfMeasurement = Some(unitOfMeasurement), dynamicRange = Some(dynamicRange))

  def gauge(name: String, tags: Map[String, String])(valueCollector: CurrentValueCollector): Gauge =
    registerGauge(name, valueCollector, tags)

  def gauge(name: String, tags: Map[String, String], unitOfMeasurement: UnitOfMeasurement)(valueCollector: CurrentValueCollector): Gauge =
    registerGauge(name, valueCollector, tags, Some(unitOfMeasurement))

  def gauge(name: String, tags: Map[String, String], dynamicRange: DynamicRange)(valueCollector: CurrentValueCollector): Gauge =
    registerGauge(name, valueCollector, tags, dynamicRange = Some(dynamicRange))

  def gauge(name: String, tags: Map[String, String], unitOfMeasurement: UnitOfMeasurement, dynamicRange: DynamicRange)(valueCollector: CurrentValueCollector): Gauge =
    registerGauge(name, valueCollector, tags, Some(unitOfMeasurement), Some(dynamicRange))

  def removeGauge(name: String): Boolean =
    removeGauge(name, Map.empty)

  def removeGauge(name: String, tags: Map[String, String]): Boolean

  def registerGauge(name: String, valueCollector: CurrentValueCollector, tags: Map[String, String] = Map.empty,
    unitOfMeasurement: Option[UnitOfMeasurement] = None, dynamicRange: Option[DynamicRange] = None,
    refreshInterval: Option[FiniteDuration] = None): Gauge

  //
  // Counters registration and removal
  //

  def counter(name: String): Counter =
    registerCounter(name)

  def counter(name: String, unitOfMeasurement: UnitOfMeasurement): Counter =
    registerCounter(name, unitOfMeasurement = Some(unitOfMeasurement))

  def counter(name: String, tags: Map[String, String]): Counter =
    registerCounter(name, tags)

  def counter(name: String, tags: Map[String, String], unitOfMeasurement: UnitOfMeasurement): Counter =
    registerCounter(name, tags, Some(unitOfMeasurement))

  def removeCounter(name: String): Boolean =
    removeCounter(name, Map.empty)

  def removeCounter(name: String, tags: Map[String, String]): Boolean

  def registerCounter(name: String, tags: Map[String, String] = Map.empty, unitOfMeasurement: Option[UnitOfMeasurement] = None,
    dynamicRange: Option[DynamicRange] = None): Counter

  //
  // Entities registration and removal
  //

  def entity[T <: EntityRecorder](recorderFactory: EntityRecorderFactory[T], name: String): T =
    entity(recorderFactory, Entity(name, recorderFactory.category))

  def entity[T <: EntityRecorder](recorderFactory: EntityRecorderFactory[T], name: String, tags: Map[String, String]): T =
    entity(recorderFactory, Entity(name, recorderFactory.category, tags))

  def removeEntity(name: String, category: String): Boolean =
    removeEntity(Entity(name, category, Map.empty))

  def removeEntity(name: String, category: String, tags: Map[String, String]): Boolean =
    removeEntity(Entity(name, category, tags))

  def removeEntity(entity: Entity): Boolean

  def entity[T <: EntityRecorder](recorderFactory: EntityRecorderFactory[T], entity: Entity): T

  def find(name: String, category: String): Option[EntityRecorder] =
    find(Entity(name, category))

  def find(name: String, category: String, tags: Map[String, String]): Option[EntityRecorder] =
    find(Entity(name, category, tags))

  def find(entity: Entity): Option[EntityRecorder]

  def subscribe(filter: SubscriptionFilter, subscriber: ActorRef): Unit =
    subscribe(filter, subscriber, permanently = true)

  def subscribe(category: String, selection: String, subscriber: ActorRef, permanently: Boolean): Unit =
    subscribe(SubscriptionFilter(category, selection), subscriber, permanently)

  def subscribe(category: String, selection: String, subscriber: ActorRef): Unit =
    subscribe(SubscriptionFilter(category, selection), subscriber, permanently = true)

  def subscribe(filter: SubscriptionFilter, subscriber: ActorRef, permanently: Boolean): Unit

  def unsubscribe(subscriber: ActorRef): Unit

  def buildDefaultCollectionContext: CollectionContext

  def instrumentFactory(category: String): InstrumentFactory
}

private[kamon] class MetricsModuleImpl(config: Config) extends MetricsModule {
  import kamon.util.TriemapAtomicGetOrElseUpdate.Syntax

  private val _trackedEntities = TrieMap.empty[Entity, EntityRecorder]
  private val _subscriptions = new LazyActorRef

  val settings = MetricsSettings(config)

  def shouldTrack(entity: Entity): Boolean =
    settings.entityFilters.get(entity.category).map {
      filter ⇒ filter.accept(entity.name)

    } getOrElse (settings.trackUnmatchedEntities)

  def registerHistogram(name: String, tags: Map[String, String], unitOfMeasurement: Option[UnitOfMeasurement],
    dynamicRange: Option[DynamicRange]): Histogram = {

    val histogramEntity = Entity(name, SingleInstrumentEntityRecorder.Histogram, tags)
    val recorder = _trackedEntities.atomicGetOrElseUpdate(histogramEntity, {
      val factory = instrumentFactory(histogramEntity.category)
      HistogramRecorder(HistogramKey(histogramEntity.category, unitOfMeasurement.getOrElse(UnitOfMeasurement.Unknown)),
        factory.createHistogram(name, dynamicRange))
    }, _.cleanup)

    recorder.asInstanceOf[HistogramRecorder].instrument
  }

  def removeHistogram(name: String, tags: Map[String, String]): Boolean =
    _trackedEntities.remove(Entity(name, SingleInstrumentEntityRecorder.Histogram, tags)).isDefined

  def registerMinMaxCounter(name: String, tags: Map[String, String], unitOfMeasurement: Option[UnitOfMeasurement], dynamicRange: Option[DynamicRange],
    refreshInterval: Option[FiniteDuration]): MinMaxCounter = {

    val minMaxCounterEntity = Entity(name, SingleInstrumentEntityRecorder.MinMaxCounter, tags)
    val recorder = _trackedEntities.atomicGetOrElseUpdate(minMaxCounterEntity, {
      val factory = instrumentFactory(minMaxCounterEntity.category)
      MinMaxCounterRecorder(MinMaxCounterKey(minMaxCounterEntity.category, unitOfMeasurement.getOrElse(UnitOfMeasurement.Unknown)),
        factory.createMinMaxCounter(name, dynamicRange, refreshInterval))
    }, _.cleanup)

    recorder.asInstanceOf[MinMaxCounterRecorder].instrument
  }

  def removeMinMaxCounter(name: String, tags: Map[String, String]): Boolean =
    _trackedEntities.remove(Entity(name, SingleInstrumentEntityRecorder.MinMaxCounter, tags)).isDefined

  def registerGauge(name: String, valueCollector: CurrentValueCollector, tags: Map[String, String] = Map.empty,
    unitOfMeasurement: Option[UnitOfMeasurement] = None, dynamicRange: Option[DynamicRange] = None,
    refreshInterval: Option[FiniteDuration] = None): Gauge = {

    val gaugeEntity = Entity(name, SingleInstrumentEntityRecorder.Gauge, tags)
    val recorder = _trackedEntities.atomicGetOrElseUpdate(gaugeEntity, {
      val factory = instrumentFactory(gaugeEntity.category)
      GaugeRecorder(MinMaxCounterKey(gaugeEntity.category, unitOfMeasurement.getOrElse(UnitOfMeasurement.Unknown)),
        factory.createGauge(name, dynamicRange, refreshInterval, valueCollector))
    }, _.cleanup)

    recorder.asInstanceOf[GaugeRecorder].instrument
  }

  def removeGauge(name: String, tags: Map[String, String]): Boolean =
    _trackedEntities.remove(Entity(name, SingleInstrumentEntityRecorder.Gauge, tags)).isDefined

  def registerCounter(name: String, tags: Map[String, String] = Map.empty, unitOfMeasurement: Option[UnitOfMeasurement] = None,
    dynamicRange: Option[DynamicRange] = None): Counter = {

    val counterEntity = Entity(name, SingleInstrumentEntityRecorder.Counter, tags)
    val recorder = _trackedEntities.atomicGetOrElseUpdate(counterEntity, {
      val factory = instrumentFactory(counterEntity.category)
      CounterRecorder(CounterKey(counterEntity.category, unitOfMeasurement.getOrElse(UnitOfMeasurement.Unknown)),
        factory.createCounter())
    }, _.cleanup)

    recorder.asInstanceOf[CounterRecorder].instrument
  }

  def removeCounter(name: String, tags: Map[String, String]): Boolean =
    _trackedEntities.remove(Entity(name, SingleInstrumentEntityRecorder.Counter, tags)).isDefined

  def entity[T <: EntityRecorder](recorderFactory: EntityRecorderFactory[T], entity: Entity): T = {
    _trackedEntities.atomicGetOrElseUpdate(entity, {
      recorderFactory.createRecorder(instrumentFactory(recorderFactory.category))
    }, _.cleanup).asInstanceOf[T]
  }

  def removeEntity(entity: Entity): Boolean =
    _trackedEntities.remove(entity).isDefined

  def find(entity: Entity): Option[EntityRecorder] =
    _trackedEntities.get(entity)

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

private[kamon] object MetricsModuleImpl {

  def apply(config: Config) =
    new MetricsModuleImpl(config)
}

