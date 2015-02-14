/* =========================================================================================
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

package kamon.metric

import java.lang.management.ManagementFactory

import com.typesafe.config.ConfigFactory
import kamon.system.jmx.GarbageCollectionMetrics
import kamon.testkit.BaseKamonSpec
import scala.collection.JavaConverters._

class SystemMetricsSpec extends BaseKamonSpec("system-metrics-spec") with RedirectLogging {

  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon.metric {
        |  tick-interval = 1 hour
        |}
        |
        |akka {
        |  extensions = ["kamon.system.SystemMetrics"]
        |}
      """.stripMargin)

  override protected def beforeAll(): Unit =
    Thread.sleep(2000) // Give some room to the recorders to store some values.

  "the Kamon System Metrics module" should {
    "record user, system, wait, idle and stolen CPU metrics" in {
      val cpuMetrics = takeSnapshotOf("cpu", "system-metric")

      cpuMetrics.histogram("cpu-user").get.numberOfMeasurements should be > 0L
      cpuMetrics.histogram("cpu-system").get.numberOfMeasurements should be > 0L
      cpuMetrics.histogram("cpu-wait").get.numberOfMeasurements should be > 0L
      cpuMetrics.histogram("cpu-idle").get.numberOfMeasurements should be > 0L
      cpuMetrics.histogram("cpu-stolen").get.numberOfMeasurements should be > 0L
    }

    "record count and time garbage collection metrics" in {
      val availableGarbageCollectors = ManagementFactory.getGarbageCollectorMXBeans.asScala.filter(_.isValid)

      for (collectorName ← availableGarbageCollectors) {
        val sanitizedName = GarbageCollectionMetrics.sanitizeCollectorName(collectorName.getName)
        val collectorMetrics = takeSnapshotOf(s"$sanitizedName-garbage-collector", "system-metric")

        collectorMetrics.gauge("garbage-collection-count").get.numberOfMeasurements should be > 0L
        collectorMetrics.gauge("garbage-collection-time").get.numberOfMeasurements should be > 0L
      }
    }

    "record used, max and committed heap metrics" in {
      val heapMetrics = takeSnapshotOf("heap-memory", "system-metric")

      heapMetrics.gauge("heap-used").get.numberOfMeasurements should be > 0L
      heapMetrics.gauge("heap-max").get.numberOfMeasurements should be > 0L
      heapMetrics.gauge("heap-committed").get.numberOfMeasurements should be > 0L
    }

    "record used, max and committed non-heap metrics" in {
      val nonHeapMetrics = takeSnapshotOf("non-heap-memory", "system-metric")

      nonHeapMetrics.gauge("non-heap-used").get.numberOfMeasurements should be > 0L
      nonHeapMetrics.gauge("non-heap-max").get.numberOfMeasurements should be > 0L
      nonHeapMetrics.gauge("non-heap-committed").get.numberOfMeasurements should be > 0L
    }

    "record daemon, count and peak jvm threads metrics" in {
      val threadsMetrics = takeSnapshotOf("threads", "system-metric")

      threadsMetrics.gauge("daemon-thread-count").get.numberOfMeasurements should be > 0L
      threadsMetrics.gauge("peak-thread-count").get.numberOfMeasurements should be > 0L
      threadsMetrics.gauge("thread-count").get.numberOfMeasurements should be > 0L
    }

    "record loaded, unloaded and current class loading metrics" in {
      val classLoadingMetrics = takeSnapshotOf("class-loading", "system-metric")

      classLoadingMetrics.gauge("classes-loaded").get.numberOfMeasurements should be > 0L
      classLoadingMetrics.gauge("classes-unloaded").get.numberOfMeasurements should be > 0L
      classLoadingMetrics.gauge("classes-currently-loaded").get.numberOfMeasurements should be > 0L
    }

    "record reads, writes, queue time and service time file system metrics" in {
      val fileSystemMetrics = takeSnapshotOf("file-system", "system-metric")

      fileSystemMetrics.histogram("file-system-reads").get.numberOfMeasurements should be > 0L
      fileSystemMetrics.histogram("file-system-writes").get.numberOfMeasurements should be > 0L
    }

    "record 1 minute, 5 minutes and 15 minutes metrics load average metrics" in {
      val loadAverage = takeSnapshotOf("load-average", "system-metric")

      loadAverage.histogram("one-minute").get.numberOfMeasurements should be > 0L
      loadAverage.histogram("five-minutes").get.numberOfMeasurements should be > 0L
      loadAverage.histogram("fifteen-minutes").get.numberOfMeasurements should be > 0L
    }

    "record used, free, swap used, swap free system memory metrics" in {
      val memoryMetrics = takeSnapshotOf("memory", "system-metric")

      memoryMetrics.histogram("memory-used").get.numberOfMeasurements should be > 0L
      memoryMetrics.histogram("memory-free").get.numberOfMeasurements should be > 0L
      memoryMetrics.histogram("swap-used").get.numberOfMeasurements should be > 0L
      memoryMetrics.histogram("swap-free").get.numberOfMeasurements should be > 0L
    }

    "record rxBytes, txBytes, rxErrors, txErrors, rxDropped, txDropped network metrics" in {
      val networkMetrics = takeSnapshotOf("network", "system-metric")

      networkMetrics.histogram("tx-bytes").get.numberOfMeasurements should be > 0L
      networkMetrics.histogram("rx-bytes").get.numberOfMeasurements should be > 0L
      networkMetrics.histogram("tx-errors").get.numberOfMeasurements should be > 0L
      networkMetrics.histogram("rx-errors").get.numberOfMeasurements should be > 0L
      networkMetrics.histogram("tx-dropped").get.numberOfMeasurements should be > 0L
      networkMetrics.histogram("rx-dropped").get.numberOfMeasurements should be > 0L
    }

    "record system and user CPU percentage for the application process" in {
      val processCpuMetrics = takeSnapshotOf("process-cpu", "system-metric")

      processCpuMetrics.histogram("process-user-cpu").get.numberOfMeasurements should be > 0L
      processCpuMetrics.histogram("process-system-cpu").get.numberOfMeasurements should be > 0L
      processCpuMetrics.histogram("process-cpu").get.numberOfMeasurements should be > 0L
    }

    "record Context Switches Global, Voluntary and Non Voluntary metrics when running on Linux" in {
      if (isLinux) {
        val contextSwitchesMetrics = takeSnapshotOf("context-switches", "system-metric")

        contextSwitchesMetrics.histogram("context-switches-process-voluntary").get.numberOfMeasurements should be > 0L
        contextSwitchesMetrics.histogram("context-switches-process-non-voluntary").get.numberOfMeasurements should be > 0L
        contextSwitchesMetrics.histogram("context-switches-global").get.numberOfMeasurements should be > 0L
      }
    }
  }

  def isLinux: Boolean =
    System.getProperty("os.name").indexOf("Linux") != -1

}
