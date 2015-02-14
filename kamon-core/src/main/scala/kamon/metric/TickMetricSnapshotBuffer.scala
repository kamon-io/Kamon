/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

import akka.actor.{ Props, Actor, ActorRef }
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.TickMetricSnapshotBuffer.FlushBuffer
import kamon.metric.instrument.CollectionContext
import kamon.util.MapMerge

import scala.concurrent.duration.FiniteDuration

class TickMetricSnapshotBuffer(flushInterval: FiniteDuration, receiver: ActorRef) extends Actor {
  import MapMerge.Syntax

  val flushSchedule = context.system.scheduler.schedule(flushInterval, flushInterval, self, FlushBuffer)(context.dispatcher)
  val collectionContext: CollectionContext = Kamon.metrics.buildDefaultCollectionContext

  def receive = empty

  def empty: Actor.Receive = {
    case tick: TickMetricSnapshot ⇒ context become (buffering(tick))
    case FlushBuffer              ⇒ // Nothing to flush.
  }

  def buffering(buffered: TickMetricSnapshot): Actor.Receive = {
    case TickMetricSnapshot(_, to, tickMetrics) ⇒
      val combinedMetrics = buffered.metrics.merge(tickMetrics, (l, r) ⇒ l.merge(r, collectionContext))
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
}

object TickMetricSnapshotBuffer {
  case object FlushBuffer

  def props(flushInterval: FiniteDuration, receiver: ActorRef): Props =
    Props[TickMetricSnapshotBuffer](new TickMetricSnapshotBuffer(flushInterval, receiver))
}
