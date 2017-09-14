/* =========================================================================================
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

package kamon.metrics

import java.lang.management.ManagementFactory

import kamon.Kamon
import kamon.system.SystemMetrics
import kamon.system.SystemMetrics.isLinux
import kamon.testkit.MetricInspection
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.JavaConverters._

class SystemMetricsSpec extends WordSpecLike
  with Matchers
  with MetricInspection
  with BeforeAndAfterAll
  with Eventually
  with RedirectLogging {


  "the Kamon System Metrics module" should {
    "record user, system, wait, idle and stolen CPU metrics" in {

      Kamon.histogram("system-metric.cpu.user").distribution().count should be > 0L
      Kamon.histogram("system-metric.cpu.system").distribution().count should be > 0L
      Kamon.histogram("system-metric.cpu.wait").distribution().count should be > 0L
      Kamon.histogram("system-metric.cpu.idle").distribution().count should be > 0L
      Kamon.histogram("system-metric.cpu.stolen").distribution().count should be > 0L
    }

    "record used, max and committed heap and non-heap metrics" in {
      val heapTag =     "segment" -> "heap"
      val nonHeapTag =  "segment" -> "non-heap"
      val poolTag =     "pool" -> "direct"

      Kamon.histogram("system-metric.jmx-memory.used").refine(heapTag).distribution().count should be > 0L
      Kamon.gauge("system-metric.jmx-memory.max").refine(heapTag).value() should be > 0L
      Kamon.gauge("system-metric.jmx-memory.committed").refine(heapTag).value() should be > 0L

      Kamon.histogram("system-metric.jmx-memory.used").refine(nonHeapTag).distribution().count should be > 0L
      Kamon.gauge("system-metric.jmx-memory.max").refine(nonHeapTag).value() should be !== 0L
      Kamon.gauge("system-metric.jmx-memory.committed").refine(nonHeapTag).value() should be !== 0L

      Kamon.gauge("system-metric.jmx-memory.buffer-pool.count").refine(poolTag).value() should be > 0L
      Kamon.gauge("system-metric.jmx-memory.buffer-pool.used").refine(poolTag).value() should be > 0L
      Kamon.gauge("system-metric.jmx-memory.buffer-pool.capacity").refine(poolTag).value() should be > 0L
    }

    "record count and time garbage collection metrics" in {
      val availableGarbageCollectors = ManagementFactory.getGarbageCollectorMXBeans.asScala.filter(_.isValid)

      System.gc() //force GC event

      val gcTimeMetric = Kamon.histogram("system-metric.garbage-collection.time")

      for (collectorName ← availableGarbageCollectors) {
        val sanitizedName = sanitizeCollectorName(collectorName.getName)
        val tags = "collector" -> sanitizedName

        gcTimeMetric.refine(tags).distribution().count should be > 0L
      }
    }

    "record the hiccup time metric" in {
      val hiccupTimeMetric = Kamon.histogram("system-metric.hiccup.time")
      hiccupTimeMetric.distribution().count should be > 0L
      hiccupTimeMetric.distribution().max should be >= 0L
    }


    "record correctly updatable values for heap metrics" in {
      val data = new Array[Byte](20 * 1024 * 1024) // 20 Mb of data

      eventually(timeout(6 seconds)) {
        val heapUsed = Kamon.histogram("system-metric.jmx-memory.used")
          .refine("segment" -> "heap")
          .distribution(false)

        heapUsed.max should be > heapUsed.min
        data.length should be > 0 // Just for data usage
      }
    }

    "record daemon, count and peak jvm threads metrics" in {
      Kamon.gauge("system-metric.threads.daemon").value() should be > 0L
      Kamon.gauge("system-metric.threads.peak").value() should be > 0L
      Kamon.gauge("system-metric.threads.total").value() should be > 0L
    }

    "record loaded, unloaded and current class loading metrics" in {
      Kamon.gauge("system-metric.class-loading.loaded").value() should be > 0L
      Kamon.gauge("system-metric.class-loading.currently-loaded").value() should be > 0L
      Kamon.gauge("system-metric.class-loading.unloaded").value() should be >= 0L
    }

    "record reads, writes, queue time and service time file system metrics" in {
      Kamon.histogram("system-metric.file-system.reads").distribution().count  should be > 0L
      Kamon.histogram("system-metric.file-system.writes").distribution().count should be > 0L
    }

    "record 1 minute, 5 minutes and 15 minutes metrics load average metrics" in {
      val metricName = s"system-metric.load.average"
      val key = "aggregation"
      val one = key -> "1"
      val five = key -> "5"
      val fifteen = key -> "15"

      Kamon.histogram(metricName).refine(one).distribution().count      should be > 0L
      Kamon.histogram(metricName).refine(five).distribution().count     should be > 0L
      Kamon.histogram(metricName).refine(fifteen).distribution().count  should be > 0L
    }

    "record used, free, swap used, swap free system memory metrics" in {
      Kamon.histogram("system-metric.memory.used").distribution().count should be > 0L
      Kamon.histogram("system-metric.memory.free").distribution().count should be > 0L
      Kamon.histogram("system-metric.memory.swap-used").distribution().count should be > 0L
      Kamon.histogram("system-metric.memory.swap-free").distribution().count should be > 0L
    }

    "record rxBytes, txBytes, rxErrors, txErrors, rxDropped, txDropped network metrics" in {
      val eventMetric = Kamon.histogram("system-metric.network.packets")

      val received    = "direction" -> "received"
      val transmitted = "direction" -> "transmitted"
      val dropped     = "state" -> "dropped"
      val error       = "state" -> "error"

      Kamon.histogram("system-metric.network.rx").distribution().count should be > 0L
      Kamon.histogram("system-metric.network.tx").distribution().count should be > 0L
      eventMetric.refine(transmitted, error).distribution().count   should be > 0L
      eventMetric.refine(received, error).distribution().count      should be > 0L
      eventMetric.refine(transmitted, dropped).distribution().count should be > 0L
      eventMetric.refine(received, dropped).distribution().count    should be > 0L
    }

    "record system and user CPU percentage for the application process" in {
      Kamon.histogram("system-metric.process-cpu.user-cpu").distribution().count     should be > 0L
      Kamon.histogram("system-metric.process-cpu.system-cpu").distribution().count   should be > 0L
      Kamon.histogram("system-metric.process-cpu.process-cpu").distribution().count  should be > 0L
    }

    "record the open files for the application process" in {
      Kamon.histogram("system-metric.ulimit.open-files").distribution().count should be > 0L
    }

    "record Context Switches Global, Voluntary and Non Voluntary metrics when running on Linux" in {
      if (isLinux) {
        Kamon.histogram("system-metric.context-switches.process-voluntary").distribution().count should be > 0L
        Kamon.histogram("system-metric.context-switches.process-non-voluntary").distribution().count should be > 0L
        Kamon.histogram("system-metric.context-switches.global").distribution().count should be > 0L
      }
    }
  }

  def sanitizeCollectorName(name: String): String =
    name.replaceAll("""[^\w]""", "-").toLowerCase

  override protected def beforeAll(): Unit = {
    SystemMetrics.startCollecting()
    System.gc()
    Thread.sleep(2000) // Give some room to the recorders to store some values.
  }

  override protected def afterAll(): Unit = SystemMetrics.stopCollecting()

}
