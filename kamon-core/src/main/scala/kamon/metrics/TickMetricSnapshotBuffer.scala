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
import kamon.metrics.Subscriptions.TickMetricSnapshot
import kamon.metrics.TickMetricSnapshotBuffer.FlushBuffer
import scala.concurrent.duration.FiniteDuration


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
