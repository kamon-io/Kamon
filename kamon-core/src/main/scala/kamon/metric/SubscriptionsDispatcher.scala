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
import kamon.metric.SubscriptionsDispatcher._
import kamon.util.{ MilliTimestamp, GlobPathFilter }
import scala.concurrent.duration.FiniteDuration

/**
 *  Manages subscriptions to metrics and dispatch snapshots on every tick to all subscribers.
 */
private[kamon] class SubscriptionsDispatcher(interval: FiniteDuration, metricsExtension: MetricsModuleImpl) extends Actor {
  var lastTick = MilliTimestamp.now
  var oneShotSubscriptions = Map.empty[ActorRef, SubscriptionFilter]
  var permanentSubscriptions = Map.empty[ActorRef, SubscriptionFilter]
  val tickSchedule = context.system.scheduler.schedule(interval, interval, self, Tick)(context.dispatcher)
  val collectionContext = metricsExtension.buildDefaultCollectionContext

  def receive = {
    case Tick                                       ⇒ processTick()
    case Subscribe(filter, subscriber, permanently) ⇒ subscribe(filter, subscriber, permanently)
    case Unsubscribe(subscriber)                    ⇒ unsubscribe(subscriber)
    case Terminated(subscriber)                     ⇒ unsubscribe(subscriber)
  }

  def processTick(): Unit =
    dispatch(metricsExtension.collectSnapshots(collectionContext))

  def subscribe(filter: SubscriptionFilter, subscriber: ActorRef, permanent: Boolean): Unit = {
    def addSubscription(storage: Map[ActorRef, SubscriptionFilter]): Map[ActorRef, SubscriptionFilter] =
      storage.updated(subscriber, storage.getOrElse(subscriber, SubscriptionFilter.Empty).combine(filter))

    context.watch(subscriber)

    if (permanent)
      permanentSubscriptions = addSubscription(permanentSubscriptions)
    else
      oneShotSubscriptions = addSubscription(oneShotSubscriptions)
  }

  def unsubscribe(subscriber: ActorRef): Unit = {
    permanentSubscriptions = permanentSubscriptions - subscriber
    oneShotSubscriptions = oneShotSubscriptions - subscriber
  }

  def dispatch(snapshots: Map[Entity, EntitySnapshot]): Unit = {
    val currentTick = MilliTimestamp.now

    dispatchSelections(lastTick, currentTick, permanentSubscriptions, snapshots)
    dispatchSelections(lastTick, currentTick, oneShotSubscriptions, snapshots)

    lastTick = currentTick
    oneShotSubscriptions = Map.empty[ActorRef, SubscriptionFilter]
  }

  def dispatchSelections(lastTick: MilliTimestamp, currentTick: MilliTimestamp, subscriptions: Map[ActorRef, SubscriptionFilter],
    snapshots: Map[Entity, EntitySnapshot]): Unit = {

    for ((subscriber, filter) ← subscriptions) {
      val selection = snapshots.filter(group ⇒ filter.accept(group._1))
      val tickMetrics = TickMetricSnapshot(lastTick, currentTick, selection)

      subscriber ! tickMetrics
    }
  }
}

object SubscriptionsDispatcher {
  def props(interval: FiniteDuration, metricsExtension: MetricsModuleImpl): Props =
    Props(new SubscriptionsDispatcher(interval, metricsExtension))

  case object Tick
  case class Unsubscribe(subscriber: ActorRef)
  case class Subscribe(filter: SubscriptionFilter, subscriber: ActorRef, permanently: Boolean = false)
  case class TickMetricSnapshot(from: MilliTimestamp, to: MilliTimestamp, metrics: Map[Entity, EntitySnapshot])

}

trait SubscriptionFilter { self ⇒

  def accept(entity: Entity): Boolean

  final def combine(that: SubscriptionFilter): SubscriptionFilter = new SubscriptionFilter {
    override def accept(entity: Entity): Boolean = self.accept(entity) || that.accept(entity)
  }
}

object SubscriptionFilter {
  val Empty = new SubscriptionFilter {
    def accept(entity: Entity): Boolean = false
  }

  def apply(category: String, name: String): SubscriptionFilter = new SubscriptionFilter {
    val categoryPattern = new GlobPathFilter(category)
    val namePattern = new GlobPathFilter(name)

    def accept(entity: Entity): Boolean = {
      categoryPattern.accept(entity.category) && namePattern.accept(entity.name)
    }
  }
}