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
 *  Class Loading metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/7/docs/api/java/lang/management/ClassLoadingMXBean.html "ClassLoadingMXBean"]]
 */
class ClassLoadingMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val classLoadingBean = ManagementFactory.getClassLoadingMXBean

  gauge("classes-loaded", Memory.Bytes, () ⇒ {
    classLoadingBean.getTotalLoadedClassCount
  })

  gauge("classes-unloaded", Memory.Bytes, () ⇒ {
    classLoadingBean.getUnloadedClassCount
  })

  gauge("classes-currently-loaded", Memory.Bytes, () ⇒ {
    classLoadingBean.getLoadedClassCount.toLong
  })

}

object ClassLoadingMetrics extends JmxSystemMetricRecorderCompanion("class-loading") {
  def apply(instrumentFactory: InstrumentFactory): ClassLoadingMetrics =
    new ClassLoadingMetrics(instrumentFactory)
}
