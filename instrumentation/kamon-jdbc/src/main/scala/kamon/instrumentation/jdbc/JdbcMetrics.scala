/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.jdbc

import kamon.Kamon
import kamon.metric._
import kamon.tag.TagSet

object JdbcMetrics {

  val OpenConnections = Kamon.rangeSampler(
    name = "jdbc.pool.connections.open",
    description = "Tracks the number of open connections in a pool"
  )

  val BorrowedConnections = Kamon.rangeSampler(
    name = "jdbc.pool.connections.borrowed",
    description = "Tracks the number of borrowed connections in a pool"
  )

  val BorrowTime = Kamon.timer(
    name = "jdbc.pool.borrow-time",
    description = "Tracks the time it takes for the connection pool to lease a connection"
  )

  val BorrowTimeouts = Kamon.counter(
    name = "jdbc.pool.borrow-timeouts",
    description = "Counts how many times the connection pool timed out while trying to lease a connection"
  )

  val InFlightStatements = Kamon.rangeSampler(
    name = "jdbc.statements.in-flight",
    description = "Tracks the number of JDBC statements executing concurrently"
  )

  class ConnectionPoolInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    val inFlightStatements = register(InFlightStatements)
    val openConnections = register(OpenConnections)
    val borrowedConnections = register(BorrowedConnections)
    val borrowTime = register(BorrowTime)
    val borrowTimeouts = register(BorrowTimeouts)
  }

  def poolInstruments(tags: TagSet): ConnectionPoolInstruments =
    new ConnectionPoolInstruments(tags)
}
