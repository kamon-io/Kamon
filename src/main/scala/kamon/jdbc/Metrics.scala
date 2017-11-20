/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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
import kamon.metric.{Counter, Histogram, MinMaxCounter}

object Metrics {

  object Statements {
    val InFlight = Kamon.minMaxCounter("jdbc.statements.in-flight")
  }

  case class ConnectionPoolMetrics(
    tags: Map[String, String],
    openConnections: MinMaxCounter,
    borrowedConnections: MinMaxCounter,
    borrowTime: Histogram,
    borrowTimeouts: Counter
  ) {

    def cleanup(): Unit = {
      ConnectionPoolMetrics.OpenConnections.remove(tags)
      ConnectionPoolMetrics.BorrowedConnections.remove(tags)
      ConnectionPoolMetrics.BorrowTime.remove(tags)
      ConnectionPoolMetrics.BorrowTimeouts.remove(tags)
    }
  }

  object ConnectionPoolMetrics {
    val OpenConnections     = Kamon.minMaxCounter("jdbc.pool.open-connections")
    val BorrowedConnections = Kamon.minMaxCounter("jdbc.pool.borrowed-connections")
    val BorrowTime          = Kamon.histogram("jdbc.pool.borrow-time", time.nanoseconds)
    val BorrowTimeouts      = Kamon.counter("jdbc.pool.borrow-timeouts")

    def apply(tags: Map[String, String]): ConnectionPoolMetrics =
      ConnectionPoolMetrics(
        tags,
        OpenConnections.refine(tags),
        BorrowedConnections.refine(tags),
        BorrowTime.refine(tags),
        BorrowTimeouts.refine(tags)
      )
  }
}