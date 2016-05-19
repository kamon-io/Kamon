/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.metric

import kamon.metric.instrument.{ Time, InstrumentFactory }

class TraceMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {

  /**
   *  Records blah blah
   */
  val elapsedTime = histogram("elapsed-time", unitOfMeasurement = Time.Nanoseconds)
  val errors = counter("errors")
}

object TraceMetrics extends EntityRecorderFactory[TraceMetrics] {
  def category: String = "trace"
  def createRecorder(instrumentFactory: InstrumentFactory): TraceMetrics = new TraceMetrics(instrumentFactory)

  // Java API.
  def factory: EntityRecorderFactory[TraceMetrics] = this
}

class SegmentMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {

  /**
   *  Records blah blah
   */
  val elapsedTime = histogram("elapsed-time", unitOfMeasurement = Time.Nanoseconds)
  val errors = counter("errors")
}

object SegmentMetrics extends EntityRecorderFactory[SegmentMetrics] {
  def category: String = "trace-segment"
  def createRecorder(instrumentFactory: InstrumentFactory): SegmentMetrics = new SegmentMetrics(instrumentFactory)

  // Java API.
  def factory: EntityRecorderFactory[SegmentMetrics] = this
}