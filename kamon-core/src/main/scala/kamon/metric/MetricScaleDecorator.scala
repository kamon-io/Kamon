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

import akka.actor.{ Actor, ActorRef, Props }
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument._

/**
 * Can be used as a decorator to scale TickMetricSnapshot messages to given `timeUnits` and/or `memoryUnits`
 * before forwarding to original receiver
 * @param timeUnits Optional time units to scale time metrics to
 * @param memoryUnits Optional memory units to scale memory metrics to
 * @param receiver Receiver of scaled metrics snapshot, usually a backend sender
 */
class MetricScaleDecorator(timeUnits: Option[Time], memoryUnits: Option[Memory], receiver: ActorRef) extends Actor {
  require(timeUnits.isDefined || memoryUnits.isDefined,
    "Use MetricScaleDecorator only when any of units is defined")

  override def receive: Receive = {
    case tick: TickMetricSnapshot ⇒
      val scaled = tick.copy(metrics = tick.metrics.mapValues { entitySnapshot ⇒
        new DefaultEntitySnapshot(entitySnapshot.metrics.map {
          case (metricKey, metricSnapshot) ⇒
            val scaledSnapshot = (metricKey.unitOfMeasurement, timeUnits, memoryUnits) match {
              case (time: Time, Some(to), _)     ⇒ metricSnapshot.scale(time, to)
              case (memory: Memory, _, Some(to)) ⇒ metricSnapshot.scale(memory, to)
              case _                             ⇒ metricSnapshot
            }
            metricKey -> scaledSnapshot
        })
      })
      receiver forward scaled
  }
}

object MetricScaleDecorator {
  def props(timeUnits: Option[Time], memoryUnits: Option[Memory], receiver: ActorRef): Props =
    Props(new MetricScaleDecorator(timeUnits, memoryUnits, receiver))
}

