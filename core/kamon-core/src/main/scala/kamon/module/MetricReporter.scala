/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package module

import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot

/**
  * Modules implementing this trait will get registered for periodically receiving metric period snapshots. The
  * frequency of the period snapshots is controlled by the kamon.metric.tick-interval setting.
  */
trait MetricReporter extends Module {
  def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit
}

object MetricReporter {

  /**
    * Transforms a metrics PeriodSnapshot instance before delivering it to the actual reporter instance.
    */
  trait Transformation {
    def apply(snapshot: PeriodSnapshot): PeriodSnapshot
  }

  /**
    * Creates a new MetricReporter instance with all provided transformations. All transformations will be applied to
    * the period snapshots sent the provided reporter in the same order they are provided to this method.
    */
  def withTransformations(reporter: MetricReporter, transformations: Transformation*): MetricReporter =
    new Module.Wrapped with MetricReporter {

      override def stop(): Unit = reporter.stop()
      override def reconfigure(newConfig: Config): Unit = reporter.reconfigure(newConfig)
      override def originalClass: Class[_ <: Module] = reporter.getClass

      override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
        reporter.reportPeriodSnapshot(transformations.tail.foldLeft(transformations.head.apply(snapshot))((s, t) =>
          t.apply(s)
        ))
      }
    }

  /**
    * Creates a transformation that only report metrics whose name matches the provided filter.
    */
  def filterMetrics(filterName: String): Transformation = new Transformation {
    override def apply(snapshot: PeriodSnapshot): PeriodSnapshot = {
      val filter = Kamon.filter(filterName)
      snapshot.copy(
        counters = snapshot.counters.filter(m => filter.accept(m.name)),
        gauges = snapshot.gauges.filter(m => filter.accept(m.name)),
        histograms = snapshot.histograms.filter(m => filter.accept(m.name)),
        timers = snapshot.timers.filter(m => filter.accept(m.name)),
        rangeSamplers = snapshot.rangeSamplers.filter(m => filter.accept(m.name))
      )
    }
  }
}
