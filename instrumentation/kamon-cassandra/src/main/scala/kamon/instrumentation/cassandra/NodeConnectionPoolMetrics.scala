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
import kamon.instrumentation.cassandra.CassandraInstrumentation.Node
import CassandraInstrumentation.Tags
import kamon.metric._
import kamon.tag.TagSet

object NodeConnectionPoolMetrics {
  private val NodePoolPrefix = "cassandra.driver.node.pool."

  val BorrowTime = Kamon.timer(
    name = NodePoolPrefix + "borrow-time",
    description = "Time spent acquiring connection to the node"
  )

  val OpenConnections = Kamon.rangeSampler(
    name = NodePoolPrefix + "connections.open",
    description = "Tracks the number of open connections to a node"
  )

  val InFlight = Kamon.histogram(
    name = NodePoolPrefix + "in-flight",
    description = "Tracks the Number of in-flight request sent to a node"
  )

  val Errors = Kamon.counter(
    name = NodePoolPrefix + "errors",
    description = "Counts the number of failed executions"
  )

  val Timeouts = Kamon.counter(
    name = NodePoolPrefix + "timeouts",
    description = "Counts the Number of timed-out executions"
  )

  val Cancelled = Kamon.counter(
    name = NodePoolPrefix + "cancelled",
    description = "Counts the number of cancelled executions"
  )

  val TriggeredSpeculations = Kamon.counter(
    name = NodePoolPrefix + "retries",
    description = "Counts the number of retried executions"
  )

  class NodeConnectionPoolInstruments(node: Node) extends InstrumentGroup(createNodeTags(node)) {
    val borrow: Timer = register(BorrowTime)
    val openConnections: RangeSampler = register(OpenConnections)
    val inFlight: Histogram = register(InFlight)
    val clientErrors: Counter = register(Errors, Tags.ErrorSource, "client")
    val serverErrors: Counter = register(Errors, Tags.ErrorSource, "server")
    val timeouts: Counter = register(Timeouts)
    val canceled: Counter = register(Cancelled)
    val triggeredSpeculations: Counter = register(TriggeredSpeculations)
  }

  private def createNodeTags(node: Node): TagSet =
    TagSet.from(
      Map(
        Tags.DC -> node.dc,
        Tags.Rack -> node.rack,
        Tags.Node -> node.address
      )
    )

}
