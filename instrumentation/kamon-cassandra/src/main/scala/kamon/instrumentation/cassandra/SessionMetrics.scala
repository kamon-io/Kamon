/*
 *  ==========================================================================================
 *  Copyright © 2013-2020 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon.instrumentation.cassandra

import kamon.Kamon
import kamon.instrumentation.cassandra.CassandraInstrumentation.{Node, Tags}
import kamon.metric.{Counter, InstrumentGroup, RangeSampler, Timer}
import kamon.tag.TagSet

object SessionMetrics {
  private val sessionPrefix = "cassandra.driver.session."

  val PoolBorrowTime = Kamon.timer(
    name        = sessionPrefix + "borrow-time",
    description = "Time spent acquiring connection from the pool"
  )

  val ConnectionPoolSize = Kamon.rangeSampler(
    name        = sessionPrefix + "size",
    description = "Connection pool size for this host"
  )

  val TrashedConnections = Kamon.counter(
    name        = sessionPrefix + "trashed",
    description = "Number of trashed connections for this host"
  )

  val InFlight = Kamon.rangeSampler(
    name        = sessionPrefix + "in-flight",
    description = "Number of in-flight requests in this session"
  )

  val Speculations = Kamon.counter(
    name        = sessionPrefix + "speculative-executions",
    description = "Number of speculative executions performed"
  )

  val Retries = Kamon.counter(
    name        = sessionPrefix + "retries",
    description = "Number of retried executions"
  )

  val Errors = Kamon.counter(
    name        = sessionPrefix + "errors",
    description = "Number of client errors during execution"
  )

  val Timeouts = Kamon.counter(
    name        = sessionPrefix + "timeouts",
    description = "Number of timed-out executions"
  )

  val Canceled = Kamon.counter(
    name        = sessionPrefix + "canceled",
    description = "Number of canceled executions"
  )

  class SessionInstruments(node: Node) extends InstrumentGroup(TagSet.of(Tags.Cluster, node.cluster)) {
    val trashedConnections: Counter      = register(TrashedConnections)
    val borrow:             Timer        = register(PoolBorrowTime)
    val size:               RangeSampler = register(ConnectionPoolSize)
    val inFlightRequests:   RangeSampler = register(InFlight)
    val speculations:       Counter      = register(Speculations)
    val retries:            Counter      = register(Retries)
    val clientErrors:       Counter      = register(Errors, Tags.ErrorSource, "client")
    val serverErrors:       Counter      = register(Errors, Tags.ErrorSource, "server")
    val timeouts:           Counter      = register(Timeouts)
    val canceled:           Counter      = register(Canceled)
  }
}
