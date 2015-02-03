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
 *  Non Heap Memory metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/7/docs/api/java/lang/management/MemoryMXBean.html "MemoryMXBean"]]
 */
class NonHeapMemoryMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val memoryBean = ManagementFactory.getMemoryMXBean
  def nonHeapUsage = memoryBean.getNonHeapMemoryUsage

  gauge("non-heap-used", Memory.Bytes, () ⇒ {
    nonHeapUsage.getUsed
  })

  gauge("non-heap-max", Memory.Bytes, () ⇒ {
    val max = nonHeapUsage.getMax

    // .getMax can return -1 if the max is not defined.
    if (max >= 0) max
    else 0
  })

  gauge("non-heap-committed", Memory.Bytes, () ⇒ {
    nonHeapUsage.getCommitted
  })

}

object NonHeapMemoryMetrics extends JmxSystemMetricRecorderCompanion("non-heap-memory") {
  def apply(instrumentFactory: InstrumentFactory): NonHeapMemoryMetrics =
    new NonHeapMemoryMetrics(instrumentFactory)
}
