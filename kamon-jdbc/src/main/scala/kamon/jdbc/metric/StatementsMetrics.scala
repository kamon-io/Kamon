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

import kamon.metric._
import kamon.metric.instrument.{ Time, InstrumentFactory }

class StatementsMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val reads = histogram("reads", Time.Nanoseconds)
  val writes = histogram("writes", Time.Nanoseconds)
  val slows = counter("slows")
  val errors = counter("errors")
}

object StatementsMetrics extends EntityRecorderFactory[StatementsMetrics] {
  def category: String = "jdbc-statements"
  def createRecorder(instrumentFactory: InstrumentFactory): StatementsMetrics = new StatementsMetrics(instrumentFactory)
}