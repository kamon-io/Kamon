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

      val userTag = "mode" -> "user"
      val systemTag = "mode" -> "system"
      val waitTag = "mode" -> "wait"
      val idleTag = "mode" -> "idle"
      val stolenTag = "mode" -> "stolen"

      val modes = userTag :: systemTag :: waitTag :: idleTag :: stolenTag :: Nil

      modes.foreach(modeTag => Kamon.histogram("host.cpu").refine("component" -> "system-metrics", modeTag).distribution().count should be > 0L)
    }

    "record used, max and committed heap and non-heap metrics" in {

      val componentTag = "component" -> "system-metrics"

      val usedTag = "measure" -> "used"
      val maxTag = "measure" -> "max"
      val committedTag = "measure" -> "committed"
      val capacityTag = "measure" -> "capacity"

      val poolDirectTag = "pool" -> "direct"

      val memoryMeasures = usedTag :: maxTag :: committedTag :: Nil

      //Heap
      memoryMeasures.foreach {
        measureTag => Kamon.histogram("jvm.memory").refine(componentTag, measureTag, "segment" -> "heap").distribution().count should be > 0L
      }

      //Non Heap
      memoryMeasures.foreach { measureTag =>
        Kamon.histogram("jvm.memory").refine(componentTag, measureTag, "segment" -> "non-heap").distribution().count should be > 0L
      }

      //Memory Pool
      Kamon.gauge("jvm.memory.buffer-pool.count").refine(componentTag, poolDirectTag).value() should be > 0L
      Kamon.gauge("jvm.memory.buffer-pool.usage").refine(componentTag, poolDirectTag, usedTag).value() should be > 0L
      Kamon.gauge("jvm.memory.buffer-pool.usage").refine(componentTag, poolDirectTag, capacityTag).value() should be > 0L
    }

    "record count and time garbage collection metrics" in {
      val availableGarbageCollectors = ManagementFactory.getGarbageCollectorMXBeans.asScala.filter(_.isValid)

      System.gc() //force GC event

      //Collectors
      for (collectorName ← availableGarbageCollectors) {
        val sanitizedName = sanitizeCollectorName(collectorName.getName)
        val collectorTags = "collector" -> sanitizedName

        Kamon.histogram("jvm.gc").refine("component" -> "system-metrics", collectorTags).distribution().count should be > 0L
      }

      //Promotion
      Seq("survivor", "old").foreach { space =>
        Kamon.histogram("jvm.gc.promotion").refine("component" -> "system-metrics", "space" -> space).distribution().count should be >= 0L
      }
    }

    "record the hiccup time metric" in {
      val hiccupTimeMetric = Kamon.histogram("jvm.hiccup").refine("component" -> "system-metrics")
      hiccupTimeMetric.distribution().count should be > 0L
      hiccupTimeMetric.distribution().max should be >= 0L
    }


    "record correctly updatable values for heap metrics" in {
      val data = new Array[Byte](20 * 1024 * 1024) // 20 Mb of data

      eventually(timeout(6 seconds)) {
        val heapUsed = Kamon.histogram("jvm.memory")
          .refine("component" -> "system-metrics", "measure" -> "used", "segment" -> "heap")
          .distribution(false)

        heapUsed.max should be > heapUsed.min
        data.length should be > 0 // Just for data usage
      }
    }

    "record daemon, count and peak jvm threads metrics" in {
      Seq("daemon", "peak", "total").foreach { measure =>
        Kamon.gauge("jvm.threads").refine("component" -> "system-metrics", "measure" -> measure).value() should be > 0L
      }
    }

    "record loaded, unloaded and current class loading metrics" in {
      Seq("loaded", "unloaded", "currently-loaded").foreach { mode =>
        Kamon.gauge("jvm.class-loading").refine("component" -> "system-metrics", "mode" -> mode).value() should be >= 0L
      }
    }

    "record reads, writes, queue time and service time file system metrics" in {
      Seq("reads", "writes").foreach { operation =>
        Kamon.histogram("host.file-system").refine("component" -> "system-metrics", "operation" -> operation).distribution().count should be >= 0L
      }
    }

    "record 1 minute, 5 minutes and 15 minutes metrics load average metrics" in {
      Seq("1", "5", "15").foreach { period =>
        Kamon.histogram("host.load-average").refine("component" -> "system-metrics", "period" -> period).distribution().count should be > 0L
      }
    }

    "record used, free, swap used, swap free system memory metrics" in {
      //Memory
      Seq("used", "cached-and-buffered", "free", "total").foreach { mode =>
        Kamon.histogram("host.memory").refine("component" -> "system-metrics", "mode" -> mode).distribution().count should be > 0L
      }

      //Swap
      Seq("used", "free").foreach { mode =>
        Kamon.histogram("host.swap").refine("component" -> "system-metrics", "mode" -> mode).distribution().count should be > 0L
      }
    }

    "record rxBytes, txBytes, rxErrors, txErrors, rxDropped, txDropped network metrics" in {
      val eventMetric = Kamon.counter("host.network.packets")
      val bytesMetric = Kamon.histogram("host.network.bytes")

      val component = "component" -> "system-metrics"

      val received    = "direction" -> "received"
      val transmitted = "direction" -> "transmitted"

      val dropped     = "state" -> "dropped"
      val error       = "state" -> "error"

      bytesMetric.refine(component, received).distribution().count should be > 0L
      bytesMetric.refine(component, transmitted).distribution().count should be > 0L

      eventMetric.refine(component, transmitted, error).value() should be >= 0L
      eventMetric.refine(component, received, error).value() should be >= 0L
      eventMetric.refine(component, transmitted, dropped).value() should be >= 0L
      eventMetric.refine(component, received, dropped).value() should be >= 0L
    }

    "record system and user CPU percentage for the application process" in {
      Seq("user", "system", "total").foreach { mode =>
        Kamon.histogram("host.process-cpu").refine("component" -> "system-metrics", "mode" -> mode).distribution().count should be > 0L
      }
    }

    "record the open files for the application process" in {
      Kamon.histogram("host.ulimit").refine("component" -> "system-metrics", "limit" -> "open-files").distribution().count should be > 0L
    }

    "record Context Switches Global, Voluntary and Non Voluntary metrics when running on Linux" in {
      if (isLinux) {
        Seq("process-voluntary", "process-non-voluntary", "global").foreach { mode =>
          Kamon.histogram("host.context-switches").refine("component" -> "system-metrics", "mode" -> mode).distribution().count should be > 0L
        }
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
