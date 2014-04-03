/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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
package kamon.statsd

import akka.actor.{ Props, Actor, ActorRef }
import kamon.metrics._
import kamon.metrics.Subscriptions.TickMetricSnapshot
import kamon.metrics.ActorMetrics.ActorMetricSnapshot

class StatsDMetricTranslator extends Actor {
  //val metricsSender =


  def receive = {
    case TickMetricSnapshot(from, to, metrics) ⇒


  }

  def transformActorMetric(actorIdentity: ActorMetrics, snapshot: ActorMetricSnapshot): Vector[StatsD.Metric] = {
    // TODO: Define metrics namespacing.
    roll(actorIdentity.name, snapshot.timeInMailbox, StatsD.Timing)
  }

  def roll(key: String, snapshot: MetricSnapshotLike, metricBuilder: (String, Long, Double) => StatsD.Metric): Vector[StatsD.Metric] = {
    val builder = Vector.newBuilder[StatsD.Metric]
    for(measurement <- snapshot.measurements) {
      val samplingRate = 1D / measurement.count
      builder += metricBuilder.apply(key, measurement.value, samplingRate)
    }

    builder.result()
  }


}

object StatsDMetricTranslator {
  def props: Props = Props(new StatsDMetricTranslator)
}
