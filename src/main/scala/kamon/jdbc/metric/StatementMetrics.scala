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

package kamon.jdbc.metric

import kamon.Kamon
import kamon.metric._
import kamon.metric.instrument.{ InstrumentFactory, Time }

class StatementMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val inFlightStatements = minMaxCounter("in-flight")
  val queries = histogram("queries")
  val updates = histogram("updates")
  val batches = histogram("batches")
  val genericExecute = histogram("generic-execute")
  val slowStatements = counter("slows-statements")
  val errors = counter("errors")
}

object StatementMetrics extends EntityRecorderFactory[StatementMetrics] {
  def category: String = "jdbc-statement"
  def createRecorder(instrumentFactory: InstrumentFactory): StatementMetrics = new StatementMetrics(instrumentFactory)
}

class ConnectionPoolMetrics(instrumentFactory: InstrumentFactory) extends StatementMetrics(instrumentFactory) {
  val openConnections = minMaxCounter("open-connections")
  val borrowedConnections = minMaxCounter("borrowed-connections")
}

object ConnectionPoolMetrics {
  def create(poolVendor: String, poolName: String): ConnectionPoolMetrics = {
    Kamon.metrics.entity(new EntityRecorderFactory[ConnectionPoolMetrics] {
      override def category: String = poolVendor
      override def createRecorder(instrumentFactory: InstrumentFactory): ConnectionPoolMetrics =
        new ConnectionPoolMetrics(instrumentFactory)
    }, poolName)
  }
}