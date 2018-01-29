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

package kamon.influxdb

import java.lang.management.ManagementFactory

import kamon.Kamon
import kamon.metric.instrument.{Counter, InstrumentFactory}
import kamon.metric.{Entity, EntityRecorderFactory, GenericEntityRecorder}
import kamon.util.MilliTimestamp

trait SenderFixture extends TagNormalizer {
  val hostName = normalize(ManagementFactory.getRuntimeMXBean.getName.split('@')(1))

  val testEntity = Entity("user/kamon", "test")
  val from = MilliTimestamp.now
  val to = MilliTimestamp.now

  def buildRecorder(name: String): TestEntityRecorder =
    Kamon.metrics.entity(TestEntityRecorder, name)
}

class TestEntityRecorder(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val histogramOne = histogram("metric-one")
  val counter: Counter = counter("metric-two")
}

object TestEntityRecorder extends EntityRecorderFactory[TestEntityRecorder] {
  def category: String = "test"
  def createRecorder(instrumentFactory: InstrumentFactory): TestEntityRecorder = new TestEntityRecorder(instrumentFactory)
}
