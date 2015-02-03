/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.system.jmx

import java.lang.management.ManagementFactory

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.{ Memory, InstrumentFactory }

/**
 *  Heap Memory metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/7/docs/api/java/lang/management/MemoryMXBean.html "MemoryMXBean"]]
 */
class HeapMemoryMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val memoryBean = ManagementFactory.getMemoryMXBean
  def nonHeapUsage = memoryBean.getHeapMemoryUsage

  gauge("heap-used", Memory.Bytes, () ⇒ {
    nonHeapUsage.getUsed
  })

  gauge("heap-max", Memory.Bytes, () ⇒ {
    nonHeapUsage.getMax
  })

  gauge("heap-committed", Memory.Bytes, () ⇒ {
    nonHeapUsage.getCommitted
  })

}

object HeapMemoryMetrics extends JmxSystemMetricRecorderCompanion("heap-memory") {
  def apply(instrumentFactory: InstrumentFactory): HeapMemoryMetrics =
    new HeapMemoryMetrics(instrumentFactory)
}
