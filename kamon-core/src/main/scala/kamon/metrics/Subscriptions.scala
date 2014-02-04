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

import akka.actor.{Props, ActorRef, Actor}
import kamon.metrics.Subscriptions.{ MetricGroupFilter, FlushMetrics, TickMetricSnapshot, Subscribe }
import kamon.util.GlobPathFilter
import scala.concurrent.duration.{FiniteDuration, Duration}
import java.util.concurrent.TimeUnit
import kamon.Kamon
import kamon.metrics.TickMetricSnapshotBuffer.FlushBuffer

class Subscriptions extends Actor {
  import context.system

  val config = context.system.settings.config
  val tickInterval = Duration(config.getNanoseconds("kamon.metrics.tick-interval"), TimeUnit.NANOSECONDS)
  val flushMetricsSchedule = context.system.scheduler.schedule(tickInterval, tickInterval, self, FlushMetrics)(context.dispatcher)

  var lastTick: Long = System.currentTimeMillis()
  var subscribedPermanently: Map[MetricGroupFilter, List[ActorRef]] = Map.empty
  var subscribedForOneShot: Map[MetricGroupFilter, List[ActorRef]] = Map.empty

  def receive = {
    case Subscribe(category, selection, permanent) ⇒ subscribe(category, selection, permanent)
    case FlushMetrics                              ⇒ flush()
  }

  def subscribe(category: MetricGroupCategory, selection: String, permanent: Boolean): Unit = {
    val filter = MetricGroupFilter(category, new GlobPathFilter(selection))
    if (permanent) {
      val receivers = subscribedPermanently.get(filter).getOrElse(Nil)
      subscribedPermanently = subscribedPermanently.updated(filter, sender :: receivers)

    } else {
      val receivers = subscribedForOneShot.get(filter).getOrElse(Nil)
      subscribedForOneShot = subscribedForOneShot.updated(filter, sender :: receivers)
    }

  }

  def flush(): Unit = {
    val currentTick = System.currentTimeMillis()
    val snapshots = Kamon(Metrics).collect

    dispatchSelectedMetrics(lastTick, currentTick, subscribedPermanently, snapshots)
    dispatchSelectedMetrics(lastTick, currentTick, subscribedForOneShot, snapshots)

    lastTick = currentTick
    subscribedForOneShot = Map.empty
  }

  def dispatchSelectedMetrics(lastTick: Long, currentTick: Long, subscriptions: Map[MetricGroupFilter, List[ActorRef]],
                              snapshots: Map[MetricGroupIdentity, MetricGroupSnapshot]): Unit = {

    for ((filter, receivers) ← subscriptions) yield {
      val selection = snapshots.filter(group ⇒ filter.accept(group._1))
      val tickMetrics = TickMetricSnapshot(lastTick, currentTick, selection)

      receivers.foreach(_ ! tickMetrics)
    }
  }
}

object Subscriptions {
  case object FlushMetrics
  case class Subscribe(category: MetricGroupCategory, selection: String, permanently: Boolean = false)
  case class TickMetricSnapshot(from: Long, to: Long, metrics: Map[MetricGroupIdentity, MetricGroupSnapshot])

  case class MetricGroupFilter(category: MetricGroupCategory, globFilter: GlobPathFilter) {
    def accept(identity: MetricGroupIdentity): Boolean = {
      category.equals(identity.category) && globFilter.accept(identity.name)
    }
  }
}


class TickMetricSnapshotBuffer(flushInterval: FiniteDuration, receiver: ActorRef) extends Actor {
  val flushSchedule = context.system.scheduler.schedule(flushInterval, flushInterval, self, FlushBuffer)(context.dispatcher)

  def receive = empty

  def empty: Actor.Receive = {
    case tick : TickMetricSnapshot  => context become(buffering(tick))
    case FlushBuffer                => // Nothing to flush.
  }

  def buffering(buffered: TickMetricSnapshot): Actor.Receive = {
    case TickMetricSnapshot(_, to, tickMetrics) =>
      val combinedMetrics = combineMaps(buffered.metrics, tickMetrics)(mergeMetricGroup)
      val combinedSnapshot = TickMetricSnapshot(buffered.from, to, combinedMetrics)

      context become(buffering(combinedSnapshot))

    case FlushBuffer =>
      receiver ! buffered
      context become(empty)

  }


  override def postStop(): Unit = {
    flushSchedule.cancel()
    super.postStop()
  }

  def mergeMetricGroup(left: MetricGroupSnapshot, right: MetricGroupSnapshot) = new MetricGroupSnapshot {
    val metrics = combineMaps(left.metrics, right.metrics)((l, r) => l.merge(r))
  }
}

object TickMetricSnapshotBuffer {
  case object FlushBuffer

  def props(flushInterval: FiniteDuration, receiver: ActorRef): Props =
    Props[TickMetricSnapshotBuffer](new TickMetricSnapshotBuffer(flushInterval, receiver))
}
