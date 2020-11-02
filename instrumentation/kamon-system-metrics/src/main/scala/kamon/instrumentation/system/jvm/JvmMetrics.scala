/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation.system.jvm

import java.lang.management.ManagementFactory

import kamon.Kamon
import kamon.instrumentation.system.jvm.JvmMetrics.MemoryUsageInstruments.MemoryRegionInstruments
import kamon.instrumentation.system.jvm.JvmMetricsCollector.{Collector, MemoryPool}
import kamon.metric.{Gauge, Histogram, InstrumentGroup, MeasurementUnit}
import kamon.tag.TagSet

import scala.collection.mutable

object JvmMetrics {

  val GC = Kamon.histogram(
    name = "jvm.gc",
    description = "Tracks the distribution of GC events duration",
    unit = MeasurementUnit.time.milliseconds
  )

  val GcPromotion = Kamon.histogram(
    name = "jvm.gc.promotion",
    description = "Tracks the distribution of promoted bytes to the old generation regions after a GC",
    unit = MeasurementUnit.information.bytes
  )

  val MemoryUsed = Kamon.histogram(
    name = "jvm.memory.used",
    description = "Samples the used space in a memory region",
    unit = MeasurementUnit.information.bytes
  )

  val MemoryFree = Kamon.histogram(
    name = "jvm.memory.free",
    description = "Samples the free space in a memory region",
    unit = MeasurementUnit.information.bytes
  )

  val MemoryCommitted = Kamon.gauge(
    name = "jvm.memory.committed",
    description = "Tracks the committed space in a memory region",
    unit = MeasurementUnit.information.bytes
  )

  val MemoryMax = Kamon.gauge(
    name = "jvm.memory.max",
    description = "Tracks the max space in a memory region",
    unit = MeasurementUnit.information.bytes
  )

  val MemoryPoolUsed = Kamon.histogram(
    name = "jvm.memory.pool.used",
    description = "Samples the used space in a memory pool",
    unit = MeasurementUnit.information.bytes
  )

  val MemoryPoolFree = Kamon.histogram(
    name = "jvm.memory.pool.free",
    description = "Samples the free space in a memory pool",
    unit = MeasurementUnit.information.bytes
  )

  val MemoryPoolCommitted = Kamon.gauge(
    name = "jvm.memory.pool.committed",
    description = "Tracks the committed space in a memory pool",
    unit = MeasurementUnit.information.bytes
  )

  val MemoryPoolMax = Kamon.gauge(
    name = "jvm.memory.pool.max",
    description = "Tracks the max space in a memory pool",
    unit = MeasurementUnit.information.bytes
  )

  val MemoryAllocation = Kamon.counter(
    name = "jvm.memory.allocation",
    description = "Tracks the number amount of bytes allocated",
    unit = MeasurementUnit.information.bytes
  )

  val ThreadsTotal = Kamon.gauge(
    name = "jvm.threads.total",
    description = "Tracks the current number of live threads on the JVM"
  )

  val ThreadsPeak = Kamon.gauge(
    name = "jvm.threads.peak",
    description = "Tracks the peak live thread count since the JVM started"
  )

  val ThreadsDaemon = Kamon.gauge(
    name = "jvm.threads.daemon",
    description = "Tracks the current number of daemon threads on the JVM"
  )

  val ClassesLoaded = Kamon.gauge(
    name = "jvm.class-loading.loaded",
    description = "Total number od classes loaded"
  )

  val ClassesUnloaded = Kamon.gauge(
    name = "jvm.class-loading.unloaded",
    description = "Total number od classes unloaded"
  )

  val ClassesCurrentlyLoaded = Kamon.gauge(
    name = "jvm.class-loading.currently-loaded",
    description = "Total number od classes currently loaded"
  )

  class GarbageCollectionInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    private val _collectorCache = mutable.Map.empty[String, Histogram]

    val allocation = register(MemoryAllocation)
    val promotionToOld = register(GcPromotion, "space", "old")

    def garbageCollectionTime(collector: Collector): Histogram =
      _collectorCache.getOrElseUpdate(collector.alias, {
        val collectorTags = TagSet.builder()
          .add("collector", collector.alias)
          .add("generation", collector.generation.toString)
          .build()

        register(GC, collectorTags)
      })
  }

  class MemoryUsageInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    private val _memoryRegionsCache = mutable.Map.empty[String, MemoryRegionInstruments]
    private val _memoryPoolsCache = mutable.Map.empty[String, MemoryRegionInstruments]

    def regionInstruments(regionName: String): MemoryRegionInstruments =
      _memoryRegionsCache.getOrElseUpdate(regionName, {
        val region = TagSet.of("region", regionName)

        MemoryRegionInstruments(
          register(MemoryUsed, region),
          register(MemoryFree, region),
          register(MemoryCommitted, region),
          register(MemoryMax, region)
        )
      })

    def poolInstruments(pool: MemoryPool): MemoryRegionInstruments =
      _memoryPoolsCache.getOrElseUpdate(pool.alias, {
        val region = TagSet.of("pool", pool.alias)

        MemoryRegionInstruments(
          register(MemoryPoolUsed, region),
          register(MemoryPoolFree, region),
          register(MemoryPoolCommitted, region),
          register(MemoryPoolMax, region)
        )
      })
  }

  class ClassLoadingInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    val loaded = register(ClassesLoaded)
    val unloaded = register(ClassesUnloaded)
    val currentlyLoaded = register(ClassesCurrentlyLoaded)
  }

  class ThreadsInstruments extends InstrumentGroup(TagSet.Empty) {
    val total = register(ThreadsTotal)
    val peak = register(ThreadsPeak)
    val daemon = register(ThreadsDaemon)
  }

  object MemoryUsageInstruments {
    case class MemoryRegionInstruments(
      used: Histogram,
      free: Histogram,
      committed: Gauge,
      max: Gauge
    )
  }
}


