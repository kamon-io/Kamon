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

package kamon.newrelic

import kamon.metrics.{ TraceMetrics, MetricGroupSnapshot, MetricGroupIdentity }
import kamon.metrics.TraceMetrics.ElapsedTime

trait WebTransactionMetrics {
  def collectWebTransactionMetrics(metrics: Map[MetricGroupIdentity, MetricGroupSnapshot]): List[NewRelic.Metric] = {
    val webTransactionMetrics = metrics.collect {
      case (TraceMetrics(name), groupSnapshot) ⇒
        groupSnapshot.metrics collect {
          case (ElapsedTime, snapshot) => toNewRelicMetric("HttpDispatcher", None, snapshot)
        }
    }

    webTransactionMetrics.flatten.toList
  }
}
