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

package kamon.instrumentation.cassandra.metrics

import kamon.instrumentation.cassandra.CassandraInstrumentation
import kamon.instrumentation.cassandra.CassandraInstrumentation.Node
import kamon.instrumentation.cassandra.HostConnectionPoolMetrics.HostConnectionPoolInstruments
import kamon.instrumentation.cassandra.SessionMetrics.SessionInstruments
import kamon.metric.Timer
import kamon.trace.Span

class NodeMonitor(node: Node) {
  val sessionMetrics = new SessionInstruments(node)
  val poolMetrics = if (CassandraInstrumentation.settings.trackHostConnectionPoolMetrics) {
    new HostConnectionPoolInstruments(node)
  } else null

  def applyNodeTags(span: Span): Unit = {
    CassandraInstrumentation.tagSpanWithNode(node, span)
  }

  def poolMetricsEnabled = poolMetrics != null

  def connectionsOpened(count: Int): Unit = {
    sessionMetrics.size.increment(count)
    if (poolMetricsEnabled) poolMetrics.size.increment(count)
  }

  def connectionClosed(): Unit = {
    sessionMetrics.size.decrement()
    if (poolMetricsEnabled) poolMetrics.size.decrement()
  }

  def clientError(): Unit = {
    sessionMetrics.clientErrors.increment()
    if (poolMetricsEnabled) poolMetrics.clientErrors.increment()
  }

  def serverError(): Unit = {
    sessionMetrics.serverErrors.increment()
    if (poolMetricsEnabled) poolMetrics.serverErrors.increment()
  }

  def retry(): Unit = {
    sessionMetrics.retries.increment()
  }

  def timeout(): Unit = {
    sessionMetrics.timeouts.increment()
    if (poolMetricsEnabled) poolMetrics.timeouts.increment()
  }

  def cancellation(): Unit = {
    sessionMetrics.canceled.increment()
    if (poolMetricsEnabled) poolMetrics.canceled.increment()
  }

  def speculativeExecution(): Unit = {
    sessionMetrics.speculations.increment()
    if (poolMetricsEnabled) poolMetrics.triggeredSpeculations.increment()
  }

  def executionStarted(): Unit = {
    sessionMetrics.inFlightRequests.increment()
  }

  def executionComplete(): Unit = {
    sessionMetrics.inFlightRequests.decrement()
  }

  def recordInFlightSample(value: Long): Unit =
    if (poolMetricsEnabled) poolMetrics.inFlight.record(value)

  def connectionTrashed(): Unit = {
    sessionMetrics.trashedConnections.increment()
  }

  def recordBorrow(nanos: Long): Unit = {
    sessionMetrics.borrow.record(nanos)
    if (poolMetricsEnabled) poolMetrics.borrow.record(nanos)
  }
}
