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

import kamon.Kamon


/**
 *  Class Loading metrics, as reported by JMX:
 *    - @see [[http://docs.oracle.com/javase/7/docs/api/java/lang/management/ClassLoadingMXBean.html "ClassLoadingMXBean"]]
 */
class ClassLoadingMetrics(metricPrefix: String)  extends JmxMetric {
  val classLoadingBean = ManagementFactory.getClassLoadingMXBean

  val classesLoaded                 = Kamon.gauge(metricPrefix+"loaded")
  val classesUnloaded               = Kamon.gauge(metricPrefix+"unloaded")
  val classesLoadedCurrentlyLoaded  = Kamon.gauge(metricPrefix+"currently-loaded")


  def update: Unit = {
    classesLoaded.set(classLoadingBean.getTotalLoadedClassCount)
    classesUnloaded.set(classLoadingBean.getUnloadedClassCount)
    classesLoadedCurrentlyLoaded.set(classLoadingBean.getLoadedClassCount.toLong)
  }

}

object ClassLoadingMetrics extends JmxSystemMetricRecorderCompanion("class-loading") {
  def apply(metricName: String): ClassLoadingMetrics =
    new ClassLoadingMetrics(metricPrefix)
}
