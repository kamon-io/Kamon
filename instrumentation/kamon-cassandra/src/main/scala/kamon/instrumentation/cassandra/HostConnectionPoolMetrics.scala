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
import kamon.instrumentation.cassandra.CassandraInstrumentation.Node
import CassandraInstrumentation.Tags
import kamon.metric._

object HostConnectionPoolMetrics {
  private val poolPrefix = "cassandra.driver.host-connection-pool."

  val BorrowTime = Kamon.timer(
    name        = poolPrefix + "borrow-time",
    description = "Time spent acquiring connection from the pool"
  )

  val Size = Kamon.rangeSampler(
    name        = poolPrefix + "size",
    description = "Connection pool size for this host"
  )

  val InFlight = Kamon.histogram(
    name        = poolPrefix + "in-flight",
    description = "Number of in-flight request on this connection measured at the moment a new query is issued"
  )

  val Errors = Kamon.counter(
    name        = poolPrefix + "errors",
    description = "Number of client errors during execution"
  )

  val Timeouts = Kamon.counter(
    name        = poolPrefix + "timeouts",
    description = "Number of timed-out executions"
  )

  val Canceled = Kamon.counter(
    name        = poolPrefix + "canceled",
    description = "Number of canceled executions"
  )

  val TriggeredSpeculations = Kamon.counter(
    name        = poolPrefix + "retries",
    description = "Number of retried executions"
  )

  class HostConnectionPoolInstruments(node: Node)
      extends InstrumentGroup(CassandraInstrumentation.createNodeTags(node)) {
    val borrow:                Timer        = register(BorrowTime)
    val size:                  RangeSampler = register(Size)
    val inFlight:              Histogram    = register(InFlight)
    val clientErrors:          Counter      = register(Errors, Tags.ErrorSource, "client")
    val serverErrors:          Counter      = register(Errors, Tags.ErrorSource, "server")
    val timeouts:              Counter      = register(Timeouts)
    val canceled:              Counter      = register(Canceled)
    val triggeredSpeculations: Counter      = register(TriggeredSpeculations)
  }
}
