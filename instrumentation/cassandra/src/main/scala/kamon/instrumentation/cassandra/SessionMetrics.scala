/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation.cassandra

import kamon.Kamon
import kamon.instrumentation.cassandra.CassandraInstrumentation.{Node, Tags}
import kamon.metric.{Counter, InstrumentGroup, RangeSampler, Timer}
import kamon.tag.TagSet

object SessionMetrics {
  private val sessionPrefix = "cassandra.driver.session."

  val BorrowTime = Kamon.timer(
    name = sessionPrefix + "borrow-time",
    description = "Time spent acquiring connection from the node pool"
  )

  val OpenConnections = Kamon.rangeSampler(
    name = sessionPrefix + "connections.open",
    description = "Tracks the number of open connections to all Cassandra nodes"
  )

  val TrashedConnections = Kamon.counter(
    name = sessionPrefix + "connections.trashed",
    description = "Counts the number of trashed connections"
  )

  val InFlight = Kamon.rangeSampler(
    name = sessionPrefix + "in-flight",
    description = "Tracks the number of in-flight requests sent to Cassandra"
  )

  val Speculations = Kamon.counter(
    name = sessionPrefix + "speculative-executions",
    description = "Counts the number of speculative executions performed"
  )

  val Retries = Kamon.counter(
    name = sessionPrefix + "retries",
    description = "Counts the number of retried executions"
  )

  val Errors = Kamon.counter(
    name = sessionPrefix + "errors",
    description = "Counts the number of failed executions"
  )

  val Timeouts = Kamon.counter(
    name = sessionPrefix + "timeouts",
    description = "Counts the number of timed-out executions"
  )

  val Cancelled = Kamon.counter(
    name = sessionPrefix + "cancelled",
    description = "Counts the number of cancelled executions"
  )

  class SessionInstruments extends InstrumentGroup(TagSet.Empty) {
    val trashedConnections: Counter = register(TrashedConnections)
    val borrow: Timer = register(BorrowTime)
    val openConnections: RangeSampler = register(OpenConnections)
    val inFlightRequests: RangeSampler = register(InFlight)
    val speculations: Counter = register(Speculations)
    val retries: Counter = register(Retries)
    val clientErrors: Counter = register(Errors, Tags.ErrorSource, "client")
    val serverErrors: Counter = register(Errors, Tags.ErrorSource, "server")
    val timeouts: Counter = register(Timeouts)
    val canceled: Counter = register(Cancelled)
  }
}
