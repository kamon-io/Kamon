/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

package kamon.jdbc

import kamon.Kamon
import kamon.metric.MeasurementUnit.time
import kamon.metric.{Counter, Histogram, Metric, RangeSampler}
import kamon.tag.TagSet

object Metrics {
  val openConnections: Metric.RangeSampler = Kamon.rangeSampler("jdbc.pool.open-connections")
  val borrowedConnections: Metric.RangeSampler = Kamon.rangeSampler("jdbc.pool.borrowed-connections")
  val borrowTime: Metric.Histogram = Kamon.histogram("jdbc.pool.borrow-time", time.nanoseconds)
  val borrowTimeouts: Metric.Counter = Kamon.counter("jdbc.pool.borrow-timeouts")

  object Statements {
    def inFlight = Kamon.rangeSampler("jdbc.statements.in-flight")
  }

  case class ConnectionPoolMetrics(
    tags: TagSet,
    openConnections: RangeSampler,
    borrowedConnections: RangeSampler,
    borrowTime: Histogram,
    borrowTimeouts: Counter
  ) {

    def cleanup(): Unit = {
      openConnections.remove()
      borrowedConnections.remove()
      borrowTime.remove()
      borrowTimeouts.remove()
    }
  }

  object ConnectionPoolMetrics {

    def apply(tags: Map[String, String]): ConnectionPoolMetrics ={
      val tagSet = TagSet.from(tags)
      ConnectionPoolMetrics(
        tagSet,
        openConnections.withTags(tagSet),
        borrowedConnections.withTags(tagSet),
        borrowTime.withTags(tagSet),
        borrowTimeouts.withTags(tagSet)
      )
    }

  }
}