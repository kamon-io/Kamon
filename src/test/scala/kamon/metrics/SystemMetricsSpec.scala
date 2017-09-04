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
import java.nio.ByteBuffer

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.system.SystemMetricsCollector
import kamon.system.jmx.GarbageCollectionMetrics
import kamon.testkit.BaseKamonSpec

import scala.collection.JavaConverters._

class SystemMetricsSpec extends BaseKamonSpec("system-metrics-spec") with RedirectLogging {

  val config =
    ConfigFactory.parseString(
      """
        |kamon {
        |  system-metrics {
        |    sigar-enabled = true
        |    jmx-enabled = true
        |  }
        |
        |  util {
        |    filters {
        |      system-metric {
        |        includes = ["**"]
        |      }
        |    }
        |  }
        |}
      """.stripMargin)

  override protected def beforeAll(): Unit = {
    val defaultConfig = Kamon.config()
    Kamon.reconfigure(config.withFallback(defaultConfig))
    SystemMetricsCollector.startCollecting
    System.gc()
    Thread.sleep(2000) // Give some room to the recorders to store some values.
  }

  override protected def afterAll(): Unit = SystemMetricsCollector.stopCollecting


  "the Kamon System Metrics module" should {
    "record user, system, wait, idle and stolen CPU metrics" in {

      Kamon.histogram("system-metric.cpu.user").distribution().count should be > 0L
      Kamon.histogram("system-metric.cpu.system").distribution().count should be > 0L
      Kamon.histogram("system-metric.cpu.wait").distribution().count should be > 0L
      Kamon.histogram("system-metric.cpu.idle").distribution().count should be > 0L
      Kamon.histogram("system-metric.cpu.stolen").distribution().count should be > 0L
    }

    "record count and time garbage collection metrics" in {
      val availableGarbageCollectors = ManagementFactory.getGarbageCollectorMXBeans.asScala.filter(_.isValid)


      val gcCountMetric = Kamon.counter("system-metric.garbage-collection.count")
      val gcTimeMetric = Kamon.counter("system-metric.garbage-collection.time")

      for (collectorName ← availableGarbageCollectors) {
        val sanitizedName = GarbageCollectionMetrics.sanitizeCollectorName(collectorName.getName)
        val tags = ("collector" -> sanitizedName)

        gcCountMetric.refine(tags).value() should be > 0L
        gcTimeMetric.refine(tags).value() should be > 0L
      }
    }

    "record used, max and committed heap and non-heap metrics" in {
      def p(name: String) = s"system-metric.jmx-memory.$name"

      val heapTag =     ("segment" -> "heap")
      val nonHeapTag =  ("segment" -> "non-heap")
      val poolTag =     ("pool" -> "direct")

      Kamon.histogram(p("used")).refine(heapTag).distribution().count   should be > 0L
      Kamon.gauge(p("max")).refine(heapTag).value()                     should be > 0L
      Kamon.gauge(p("commited")).refine(heapTag).value()                should be > 0L

      Kamon.histogram(p("used")).refine(nonHeapTag).distribution().count    should be > 0L
      Kamon.gauge(p("max")).refine(nonHeapTag).value()                    should be !== 0L
      Kamon.gauge(p("committed")).refine(nonHeapTag).value()              should be !== 0L

      Kamon.gauge(p("buffer-pool.count")).refine(poolTag).value()    should be > 0L
      Kamon.gauge(p("buffer-pool.used")).refine(poolTag).value()     should be > 0L
      Kamon.gauge(p("buffer-pool.capacity")).refine(poolTag).value() should be > 0L
    }

    "record correctly updatable values for heap metrics" in {
      Thread.sleep(3000)

      val data = new Array[Byte](20 * 1024 * 1024) // 20 Mb of data

      Thread.sleep(3000)

      val heapUsed = Kamon.histogram("system-metric.jmx-memory.used")
        .refine(("segment" -> "heap"))
        .distribution(false)

      heapUsed.max should be > heapUsed.min
      data.size should be > 0 // Just for data usage
    }

    "record daemon, count and peak jvm threads metrics" in {
      def p(name: String) = s"system-metric.threads.$name"

      Kamon.gauge(p("daemon")).value()  should be > 0L
      Kamon.gauge(p("peak")).value()    should be > 0L
      Kamon.gauge(p("total")).value()   should be > 0L
    }

    "record loaded, unloaded and current class loading metrics" in {
      def p(name: String) = s"system-metric.class-loading.$name"

      Kamon.gauge(p("loaded")).value()            should be > 0L
      Kamon.gauge(p("currently-loaded")).value()  should be > 0L
      Kamon.gauge(p("unloaded")).value()          should be >= 0L
    }

    "record reads, writes, queue time and service time file system metrics" in {
      def p(name: String) = s"system-metric.file-system.$name"

      Kamon.histogram(p("reads")).distribution().count  should be > 0L
      Kamon.histogram(p("writes")).distribution().count should be > 0L
    }

    "record 1 minute, 5 minutes and 15 minutes metrics load average metrics" in {
      val metricName = s"system-metric.load.average"
      val key = "aggregation"
      val one = (key -> "1")
      val five = (key -> "5")
      val fifteen = (key -> "15")

      Kamon.histogram(metricName).refine(one).distribution().count      should be > 0L
      Kamon.histogram(metricName).refine(five).distribution().count     should be > 0L
      Kamon.histogram(metricName).refine(fifteen).distribution().count  should be > 0L
    }

    "record used, free, swap used, swap free system memory metrics" in {
      def p(name: String) = s"system-metric.memory.$name"

      Kamon.histogram(p("used")).distribution().count should be > 0L
      Kamon.histogram(p("free")).distribution().count should be > 0L
      Kamon.histogram(p("swap-used")).distribution().count should be > 0L
      Kamon.histogram(p("swap-free")).distribution().count should be > 0L
    }

    "record rxBytes, txBytes, rxErrors, txErrors, rxDropped, txDropped network metrics" in {
      def p(name: String) = s"system-metric.network.$name"

      val eventMetric = Kamon.histogram(p("packets"))

      val received    = ("direction"  -> "received")
      val transmitted = ("direction"  -> "transmitted")
      val dropped     = ("state"      -> "dropped")
      val error       = ("state"      -> "error")

      Kamon.histogram(p("rx")).distribution().count                 should be > 0L
      Kamon.histogram(p("tx")).distribution().count                 should be > 0L
      eventMetric.refine(transmitted, error).distribution().count   should be > 0L
      eventMetric.refine(received, error).distribution().count      should be > 0L
      eventMetric.refine(transmitted, dropped).distribution().count should be > 0L
      eventMetric.refine(received, dropped).distribution().count    should be > 0L
    }

    "record system and user CPU percentage for the application process" in {
      def p(name: String) = s"system-metric.process-cpu.$name"

      Kamon.histogram(p("user-cpu")).distribution().count     should be > 0L
      Kamon.histogram(p("system-cpu")).distribution().count   should be > 0L
      Kamon.histogram(p("process-cpu")).distribution().count  should be > 0L
    }

    "record the open files for the application process" in {
      Kamon.histogram("system-metric.ulimit.open-files").distribution().count should be > 0L
    }

    "record Context Switches Global, Voluntary and Non Voluntary metrics when running on Linux" in {
      if (isLinux) {
        def p(name: String) = s"system-metric.context-switches.$name"

        Kamon.histogram(p("process-voluntary")).distribution().count should be > 0L
        Kamon.histogram(p("process-non-voluntary")).distribution().count should be > 0L
        Kamon.histogram(p("global")).distribution().count should be > 0L
      }
    }
  }

  def isLinux: Boolean =
    System.getProperty("os.name").indexOf("Linux") != -1

}
