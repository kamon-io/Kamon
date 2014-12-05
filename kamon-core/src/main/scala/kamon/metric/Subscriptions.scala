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
import kamon.metric.Subscriptions._
import kamon.util.GlobPathFilter
import scala.concurrent.duration.{ FiniteDuration, Duration }
import java.util.concurrent.TimeUnit
import kamon.{ MilliTimestamp, Kamon }
import kamon.metric.TickMetricSnapshotBuffer.FlushBuffer

class Subscriptions extends Actor {
  import context.system

  val flushMetricsSchedule = scheduleFlushMessage()
  val collectionContext = Kamon(Metrics).buildDefaultCollectionContext

  var lastTick: MilliTimestamp = MilliTimestamp.now
  var oneShotSubscriptions: Map[ActorRef, MetricSelectionFilter] = Map.empty
  var permanentSubscriptions: Map[ActorRef, MetricSelectionFilter] = Map.empty

  def receive = {
    case Subscribe(category, selection, subscriber, permanent) ⇒ subscribe(category, selection, subscriber, permanent)
    case Unsubscribe(subscriber) ⇒ unsubscribe(subscriber)
    case Terminated(subscriber) ⇒ unsubscribe(subscriber)
    case FlushMetrics ⇒ flush()
  }

  def subscribe(category: MetricGroupCategory, selection: String, subscriber: ActorRef, permanent: Boolean): Unit = {
    context.watch(subscriber)
    val newFilter: MetricSelectionFilter = GroupAndPatternFilter(category, new GlobPathFilter(selection))

    if (permanent) {
      permanentSubscriptions = permanentSubscriptions.updated(subscriber, newFilter combine {
        permanentSubscriptions.getOrElse(subscriber, MetricSelectionFilter.empty)
      })
    } else {
      oneShotSubscriptions = oneShotSubscriptions.updated(subscriber, newFilter combine {
        oneShotSubscriptions.getOrElse(subscriber, MetricSelectionFilter.empty)
      })
    }
  }

  def unsubscribe(subscriber: ActorRef): Unit = {
    if (permanentSubscriptions.contains(subscriber))
      permanentSubscriptions = permanentSubscriptions - subscriber

    if (oneShotSubscriptions.contains(subscriber))
      oneShotSubscriptions = oneShotSubscriptions - subscriber
  }

  def flush(): Unit = {
    val currentTick = MilliTimestamp.now
    val snapshots = collectAll()

    dispatchSelectedMetrics(lastTick, currentTick, permanentSubscriptions, snapshots)
    dispatchSelectedMetrics(lastTick, currentTick, oneShotSubscriptions, snapshots)

    lastTick = currentTick
    oneShotSubscriptions = Map.empty
  }

  def collectAll(): Map[MetricGroupIdentity, MetricGroupSnapshot] = {
    val allMetrics = Kamon(Metrics).storage
    val builder = Map.newBuilder[MetricGroupIdentity, MetricGroupSnapshot]

    allMetrics.foreach {
      case (identity, recorder) ⇒ builder += ((identity, recorder.collect(collectionContext)))
    }

    builder.result()
  }

  def dispatchSelectedMetrics(lastTick: MilliTimestamp, currentTick: MilliTimestamp, subscriptions: Map[ActorRef, MetricSelectionFilter],
    snapshots: Map[MetricGroupIdentity, MetricGroupSnapshot]): Unit = {

    for ((subscriber, filter) ← subscriptions) {
      val selection = snapshots.filter(group ⇒ filter.accept(group._1))
      val tickMetrics = TickMetricSnapshot(lastTick, currentTick, selection)

      subscriber ! tickMetrics
    }
  }

  def scheduleFlushMessage(): Cancellable = {
    val config = context.system.settings.config
    val tickInterval = Duration(config.getDuration("kamon.metrics.tick-interval", TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS)
    context.system.scheduler.schedule(tickInterval, tickInterval, self, FlushMetrics)(context.dispatcher)
  }
}

object Subscriptions {
  case object FlushMetrics
  case class Unsubscribe(subscriber: ActorRef)
  case class Subscribe(category: MetricGroupCategory, selection: String, subscriber: ActorRef, permanently: Boolean = false)
  case class TickMetricSnapshot(from: MilliTimestamp, to: MilliTimestamp, metrics: Map[MetricGroupIdentity, MetricGroupSnapshot])

  trait MetricSelectionFilter {
    def accept(identity: MetricGroupIdentity): Boolean
  }

  object MetricSelectionFilter {
    val empty = new MetricSelectionFilter {
      def accept(identity: MetricGroupIdentity): Boolean = false
    }

    implicit class CombinableMetricSelectionFilter(msf: MetricSelectionFilter) {
      def combine(that: MetricSelectionFilter): MetricSelectionFilter = new MetricSelectionFilter {
        def accept(identity: MetricGroupIdentity): Boolean = msf.accept(identity) || that.accept(identity)
      }
    }
  }

  case class GroupAndPatternFilter(category: MetricGroupCategory, globFilter: GlobPathFilter) extends MetricSelectionFilter {
    def accept(identity: MetricGroupIdentity): Boolean = {
      category.equals(identity.category) && globFilter.accept(identity.name)
    }
  }
}

class TickMetricSnapshotBuffer(flushInterval: FiniteDuration, receiver: ActorRef) extends Actor {
  val flushSchedule = context.system.scheduler.schedule(flushInterval, flushInterval, self, FlushBuffer)(context.dispatcher)
  val collectionContext = Kamon(Metrics)(context.system).buildDefaultCollectionContext

  def receive = empty

  def empty: Actor.Receive = {
    case tick: TickMetricSnapshot ⇒ context become (buffering(tick))
    case FlushBuffer              ⇒ // Nothing to flush.
  }

  def buffering(buffered: TickMetricSnapshot): Actor.Receive = {
    case TickMetricSnapshot(_, to, tickMetrics) ⇒
      val combinedMetrics = combineMaps(buffered.metrics, tickMetrics)(mergeMetricGroup)
      val combinedSnapshot = TickMetricSnapshot(buffered.from, to, combinedMetrics)

      context become (buffering(combinedSnapshot))

    case FlushBuffer ⇒
      receiver ! buffered
      context become (empty)

  }

  override def postStop(): Unit = {
    flushSchedule.cancel()
    super.postStop()
  }

  def mergeMetricGroup(left: MetricGroupSnapshot, right: MetricGroupSnapshot) = left.merge(right.asInstanceOf[left.GroupSnapshotType], collectionContext).asInstanceOf[MetricGroupSnapshot] // ??? //Combined(combineMaps(left.metrics, right.metrics)((l, r) ⇒ l.merge(r, collectionContext)))
}

object TickMetricSnapshotBuffer {
  case object FlushBuffer

  def props(flushInterval: FiniteDuration, receiver: ActorRef): Props =
    Props[TickMetricSnapshotBuffer](new TickMetricSnapshotBuffer(flushInterval, receiver))
}
