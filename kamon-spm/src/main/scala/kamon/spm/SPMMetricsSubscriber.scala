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

package kamon.spm

import akka.actor.{ ActorLogging, Props, Actor, ActorRef }
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.TickMetricSnapshotBuffer
import kamon.spm.SPMMetricsSender.Send

import scala.concurrent.duration.FiniteDuration

class SPMMetricsSubscriber(sender: ActorRef, flushInterval: FiniteDuration, subscriptions: List[(String, String)]) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    val buffer = context.system.actorOf(TickMetricSnapshotBuffer.props(flushInterval, self))
    subscriptions.foreach {
      case (category, selection) ⇒
        Kamon.metrics.subscribe(category, selection, buffer)
    }
  }

  def receive = {
    case tick: TickMetricSnapshot ⇒ {
      val metrics = tick.metrics.toList.flatMap {
        case (entry, snap) ⇒
          snap.metrics.toList.map {
            case (key, snap) ⇒
              SPMMetric(tick.to, entry.category, entry.name, key.name, key.unitOfMeasurement, snap)
          }
      }

      sender ! Send(metrics)
    }
  }
}

object SPMMetricsSubscriber {
  def props(sender: ActorRef, flushInterval: FiniteDuration, subscriptions: List[(String, String)]) =
    Props(classOf[SPMMetricsSubscriber], sender, flushInterval, subscriptions)
}
