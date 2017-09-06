/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

import kamon.Kamon
import org.slf4j.Logger

/**
 *  Class Loading metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/8/docs/api/java/lang/management/ClassLoadingMXBean.html "ClassLoadingMXBean"]]
 */
object ClassLoadingMetrics extends JmxMetricBuilder("class-loading") {
  val classLoadingBean = ManagementFactory.getClassLoadingMXBean

  val classesLoadedMetric           = Kamon.gauge(s"$metricPrefix.loaded")
  val classesUnloadedMetric         = Kamon.gauge(s"$metricPrefix.unloaded")
  val classesLoadedCurrentlyMetric  = Kamon.gauge(s"$metricPrefix.currently-loaded")

  def build(metricName: String, logger: Logger) = new JmxMetric {
    def update(): Unit = {
      classesLoadedMetric.set(classLoadingBean.getTotalLoadedClassCount)
      classesUnloadedMetric.set(classLoadingBean.getUnloadedClassCount)
      classesLoadedCurrentlyMetric.set(classLoadingBean.getLoadedClassCount.toLong)
    }
  }
}