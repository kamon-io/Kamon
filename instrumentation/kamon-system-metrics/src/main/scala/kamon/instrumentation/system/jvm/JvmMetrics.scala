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
import kamon.instrumentation.system.jvm.JvmMetrics.MemoryUsageInstruments.{
  BufferPoolInstruments,
  MemoryRegionInstruments
}
import kamon.instrumentation.system.jvm.JvmMetricsCollector.{Collector, MemoryPool, ThreadState}
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

  val MemoryUtilization = Kamon.histogram(
    name = "jvm.memory.utilization",
    description = "Tracks the percentage of heap used across old/tenured/single memory regions after GC events",
    unit = MeasurementUnit.percentage
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

  val ThreadsStates = Kamon.gauge(
    name = "jvm.threads.states",
    description = "Tracks the current number of threads on each possible state"
  )

  val ClassesLoaded = Kamon.gauge(
    name = "jvm.class-loading.loaded",
    description = "Total number of classes loaded"
  )

  val ClassesUnloaded = Kamon.gauge(
    name = "jvm.class-loading.unloaded",
    description = "Total number of classes unloaded"
  )

  val ClassesCurrentlyLoaded = Kamon.gauge(
    name = "jvm.class-loading.currently-loaded",
    description = "Total number of classes currently loaded"
  )

  val BufferPoolCount = Kamon.gauge(
    name = s"jvm.memory.buffer-pool.count",
    description = "Estimated number of buffers in the pool"
  )

  val BufferPoolUsed = Kamon.gauge(
    name = s"jvm.memory.buffer-pool.used",
    description = "Estimate of memory used by the JVM for this buffer pool in bytes",
    unit = MeasurementUnit.information.bytes
  )

  val BufferPoolCapacity = Kamon.gauge(
    name = s"jvm.memory.buffer-pool.capacity",
    description = "Estimate of the total capacity of this pool in bytes",
    unit = MeasurementUnit.information.bytes
  )

  class GarbageCollectionInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    private val _collectorCache = mutable.Map.empty[String, Histogram]

    val memoryUtilization = register(MemoryUtilization)
    val promotionToOld = register(GcPromotion, "space", "old")

    def garbageCollectionTime(collector: Collector): Histogram =
      _collectorCache.getOrElseUpdate(
        collector.alias, {
          val collectorTags = TagSet.builder()
            .add("collector", collector.alias)
            .add("generation", collector.generation.toString)
            .build()

          register(GC, collectorTags)
        }
      )
  }

  class MemoryUsageInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    val allocation = register(MemoryAllocation)

    private val _memoryRegionsCache = mutable.Map.empty[String, MemoryRegionInstruments]
    private val _memoryPoolsCache = mutable.Map.empty[String, MemoryRegionInstruments]
    private val _memoryBuffersCache = mutable.Map.empty[String, BufferPoolInstruments]

    def regionInstruments(regionName: String): MemoryRegionInstruments =
      _memoryRegionsCache.getOrElseUpdate(
        regionName, {
          val region = TagSet.of("region", regionName)

          MemoryRegionInstruments(
            register(MemoryUsed, region),
            register(MemoryFree, region),
            register(MemoryCommitted, region),
            register(MemoryMax, region)
          )
        }
      )

    def poolInstruments(pool: MemoryPool): MemoryRegionInstruments =
      _memoryPoolsCache.getOrElseUpdate(
        pool.alias, {
          val region = TagSet.of("pool", pool.alias)

          MemoryRegionInstruments(
            register(MemoryPoolUsed, region),
            register(MemoryPoolFree, region),
            register(MemoryPoolCommitted, region),
            register(MemoryPoolMax, region)
          )
        }
      )

    def bufferPoolInstruments(poolName: String): BufferPoolInstruments = {
      _memoryBuffersCache.getOrElseUpdate(
        poolName, {
          val bufferTags = tags
            .withTag("pool", poolName)

          BufferPoolInstruments(
            register(BufferPoolCount, bufferTags),
            register(BufferPoolUsed, bufferTags),
            register(BufferPoolCapacity, bufferTags)
          )
        }
      )
    }
  }

  class ClassLoadingInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    val loaded = register(ClassesLoaded)
    val unloaded = register(ClassesUnloaded)
    val currentlyLoaded = register(ClassesCurrentlyLoaded)
  }

  class ThreadsInstruments extends InstrumentGroup(TagSet.Empty) {
    private val _threadsStatesCache = mutable.Map.empty[ThreadState, Gauge]
    val total = register(ThreadsTotal)
    val peak = register(ThreadsPeak)
    val daemon = register(ThreadsDaemon)

    def threadState(threadState: ThreadState): Gauge = {
      _threadsStatesCache.getOrElseUpdate(
        threadState, {
          val stateTag = TagSet.of("state", threadState.toString)

          register(ThreadsStates, stateTag)
        }
      )
    }
  }

  object MemoryUsageInstruments {
    case class MemoryRegionInstruments(
      used: Histogram,
      free: Histogram,
      committed: Gauge,
      max: Gauge
    )

    case class BufferPoolInstruments(
      count: Gauge,
      used: Gauge,
      capacity: Gauge
    )
  }
}
