/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.riemann

import java.net.InetSocketAddress

import akka.actor.{ ActorRef, Actor }
import com.aphyr.riemann.Proto.Event
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot

abstract class MetricsSender(riemannHost: String, riemannPort: Int, metricsMapper: MetricsMapper) extends Actor {

  lazy val socketAddress = new InetSocketAddress(riemannHost, riemannPort)

  def send(tick: TickMetricSnapshot, createSender: () ⇒ ActorRef): Unit = {
    val events = for (
      (entity, snapshot) ← tick.metrics;
      (metricKey, metricSnapshot) ← snapshot.metrics
    ) yield {
      metricsMapper.toEvents(entity, metricKey, metricSnapshot)
    }

    writeToRemote(events.flatten, createSender)
  }

  def writeToRemote(events: Iterable[Event], createSender: () ⇒ ActorRef): Unit
}
