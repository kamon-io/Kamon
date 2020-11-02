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

import java.lang.management.{ManagementFactory, MemoryUsage}
import java.util.concurrent.TimeUnit

import com.sun.management.GarbageCollectionNotificationInfo
import com.sun.management.GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION
import com.typesafe.config.Config
import javax.management.openmbean.CompositeData
import javax.management.{Notification, NotificationEmitter, NotificationListener}
import kamon.Kamon
import kamon.instrumentation.system.jvm.JvmMetrics.{ClassLoadingInstruments, GarbageCollectionInstruments, MemoryUsageInstruments, ThreadsInstruments}
import kamon.instrumentation.system.jvm.JvmMetricsCollector.{Collector, MemoryPool}
import kamon.module.{Module, ModuleFactory}
import kamon.tag.TagSet

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, mapAsScalaMapConverter}
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.util.matching.Regex

class JvmMetricsCollector(ec: ExecutionContext) extends Module {
  private val _defaultTags = TagSet.of("component", "jvm")
  private val _gcListener = registerGcListener(_defaultTags)
  private val _memoryUsageInstruments = new MemoryUsageInstruments(_defaultTags)
  private val _threadsUsageInstruments = new ThreadsInstruments()
  private val _classLoadingInstruments = new ClassLoadingInstruments(_defaultTags)
  private val _jmxCollectorTask = new JmxMetricsCollectorTask(_memoryUsageInstruments, _threadsUsageInstruments, _classLoadingInstruments)
  private val _jmxCollectorSchedule = Kamon.scheduler().scheduleAtFixedRate(_jmxCollectorTask, 1, 10, TimeUnit.SECONDS)

  override def stop(): Unit = {
    _jmxCollectorSchedule.cancel(false)
    deregisterGcListener()
  }

  override def reconfigure(newConfig: Config): Unit = {}

  private def registerGcListener(defaultTags: TagSet): NotificationListener = {
    val gcInstruments = new GarbageCollectionInstruments(defaultTags)
    val gcListener = new GcNotificationListener(gcInstruments)

    ManagementFactory.getGarbageCollectorMXBeans().asScala.foreach(gcBean => {
      if(gcBean.isInstanceOf[NotificationEmitter])
        gcBean.asInstanceOf[NotificationEmitter].addNotificationListener(gcListener, null, null)
    })

    gcListener
  }

  private def deregisterGcListener(): Unit = {
    ManagementFactory.getGarbageCollectorMXBeans().asScala.foreach(gcBean => {
      if(gcBean.isInstanceOf[NotificationEmitter]) {
        gcBean.asInstanceOf[NotificationEmitter].removeNotificationListener(_gcListener)
      }
    })
  }

  class GcNotificationListener(val gcInstruments: GarbageCollectionInstruments) extends NotificationListener {
    private val _previousUsageAfterGc = TrieMap.empty[String, MemoryUsage]

    override def handleNotification(notification: Notification, handback: Any): Unit = {
      if(notification.getType() == GARBAGE_COLLECTION_NOTIFICATION) {
        val compositeData = notification.getUserData.asInstanceOf[CompositeData]
        val info = GarbageCollectionNotificationInfo.from(compositeData)
        val collector = Collector.find(info.getGcName)

        gcInstruments.garbageCollectionTime(collector).record(info.getGcInfo.getDuration)

        val usageBeforeGc = info.getGcInfo.getMemoryUsageBeforeGc.asScala
        val usageAfterGc = info.getGcInfo.getMemoryUsageAfterGc.asScala

        usageBeforeGc.foreach {
          case (regionName, regionUsageBeforeGc) =>
            val region = MemoryPool.find(regionName)

            // We assume that if the old generation grew during this GC event then some data was promoted to it and will
            // record it as promotion to the old generation.
            if(region.usage == MemoryPool.Usage.OldGeneration) {
              val regionUsageAfterGc = usageAfterGc(regionName)
              val diff = regionUsageAfterGc.getUsed - regionUsageBeforeGc.getUsed

              if(diff > 0)
                gcInstruments.promotionToOld.record(diff)

            }

            // We will record the growth of Eden spaces in between GC events as the allocation rate.
            if(region.usage == MemoryPool.Usage.Eden) {
              _previousUsageAfterGc.get(regionName).fold({

                // We wont have the previous GC value the first time an Eden region is processed so we can assume
                // that all used space before GC was just allocation.
                gcInstruments.allocation.increment(regionUsageBeforeGc.getUsed): Unit

              })(previousUsageAfterGc => {
                val currentUsageBeforeGc = regionUsageBeforeGc
                val allocated = currentUsageBeforeGc.getUsed - previousUsageAfterGc.getUsed
                if(allocated > 0)
                  gcInstruments.allocation.increment(allocated)
              })

              _previousUsageAfterGc.put(regionName, usageAfterGc(regionName))
            }
        }
      }
    }
  }

  class JmxMetricsCollectorTask(memoryUsageInstruments: MemoryUsageInstruments, threadsInstruments: ThreadsInstruments, classLoadingInstruments: ClassLoadingInstruments)
      extends Runnable {

    private val _heapUsage = memoryUsageInstruments.regionInstruments("heap")
    private val _nonHeapUsage = memoryUsageInstruments.regionInstruments("non-heap")
    private val _classLoading = classLoadingInstruments

    override def run(): Unit = {
      val threadsMxBen = ManagementFactory.getThreadMXBean()
      threadsInstruments.total.update(threadsMxBen.getThreadCount())
      threadsInstruments.peak.update(threadsMxBen.getPeakThreadCount())
      threadsInstruments.daemon.update(threadsMxBen.getDaemonThreadCount())

      val currentHeapUsage = ManagementFactory.getMemoryMXBean.getHeapMemoryUsage
      val freeHeap = Math.max(0L, currentHeapUsage.getMax -  currentHeapUsage.getUsed)
      _heapUsage.free.record(freeHeap)
      _heapUsage.used.record(currentHeapUsage.getUsed)
      _heapUsage.max.update(currentHeapUsage.getMax)
      _heapUsage.committed.update(currentHeapUsage.getCommitted)

      val currentNonHeapUsage = ManagementFactory.getMemoryMXBean.getNonHeapMemoryUsage
      val freeNonHeap = Math.max(0L, currentNonHeapUsage.getMax -  currentNonHeapUsage.getUsed)
      _nonHeapUsage.free.record(freeNonHeap)
      _nonHeapUsage.used.record(currentNonHeapUsage.getUsed)
      _nonHeapUsage.max.update(currentNonHeapUsage.getMax)
      _nonHeapUsage.committed.update(currentNonHeapUsage.getCommitted)

      val classLoadingBean = ManagementFactory.getClassLoadingMXBean
      _classLoading.loaded.update(classLoadingBean.getTotalLoadedClassCount)
      _classLoading.unloaded.update(classLoadingBean.getUnloadedClassCount)
      _classLoading.currentlyLoaded.update(classLoadingBean.getLoadedClassCount)

      ManagementFactory.getMemoryPoolMXBeans.asScala.foreach(memoryBean => {
        val poolInstruments = memoryUsageInstruments.poolInstruments(MemoryPool.find(memoryBean.getName))
        val memoryUsage = memoryBean.getUsage
        val freeMemory = Math.max(0L, memoryUsage.getMax -  memoryUsage.getUsed)

        poolInstruments.free.record(freeMemory)
        poolInstruments.used.record(memoryUsage.getUsed)
        poolInstruments.max.update(memoryUsage.getMax)
        poolInstruments.committed.update(memoryUsage.getCommitted)
      })
    }
  }
}

object JvmMetricsCollector {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new JvmMetricsCollector(settings.executionContext)
  }

  case class Collector (
    name: String,
    alias: String,
    generation: Collector.Generation
  )

  object Collector {

    sealed trait Generation
    object Generation {
      case object Young extends Generation { override def toString: String = "young" }
      case object Old extends Generation { override def toString: String = "old" }
      case object Unknown extends Generation { override def toString: String = "unknown" }
    }

    def find(collectorName: String): Collector =
      _collectorMappings.get(collectorName).getOrElse (
        Collector(collectorName, sanitizeCollectorName(collectorName), Collector.Generation.Unknown)
      )

    private val _collectorMappings: Map[String, Collector] = Map (
      "Copy"                -> Collector("Copy", "copy", Generation.Young),
      "ParNew"              -> Collector("ParNew", "par-new", Generation.Young),
      "MarkSweepCompact"    -> Collector("MarkSweepCompact", "mark-sweep-compact", Generation.Old),
      "ConcurrentMarkSweep" -> Collector("ConcurrentMarkSweep", "concurrent-mark-sweep", Generation.Old),
      "PS Scavenge"         -> Collector("PS Scavenge", "ps-scavenge", Generation.Young),
      "PS MarkSweep"        -> Collector("PS MarkSweep", "ps-mark-sweep", Generation.Old),
      "G1 Young Generation" -> Collector("G1 Young Generation", "g1-young", Generation.Young),
      "G1 Old Generation"   -> Collector("G1 Old Generation", "g1-old", Generation.Old)
    )

    private def sanitizeCollectorName(name: String): String =
      name.replaceAll("""[^\w]""", "-").toLowerCase
  }

  case class MemoryPool (
    name: String,
    alias: String,
    usage: MemoryPool.Usage
  )

  object MemoryPool {

    sealed trait Usage
    object Usage {
      case object Eden extends Usage
      case object YoungGeneration extends Usage
      case object OldGeneration extends Usage
      case object CodeCache extends Usage
      case object Metaspace extends Usage
      case object Unknown extends Usage
    }

    def find(poolName: String): MemoryPool =
      _memoryRegionMappings.get(poolName).getOrElse {
        MemoryPool(poolName, sanitize(poolName), if(poolName.endsWith("Eden Space")) Usage.Eden else Usage.Unknown)
      }

    private val _memoryRegionMappings: Map[String, MemoryPool] = Map (
      "Metaspace"               -> MemoryPool("Metaspace", "metaspace", Usage.Metaspace),
      "Code Cache"              -> MemoryPool("Code Cache", "code-cache", Usage.CodeCache),
      "Compressed Class Space"  -> MemoryPool("Compressed Class Space", "compressed-class-space", Usage.CodeCache),
      "PS Eden Space"           -> MemoryPool("PS Eden Space", "eden", Usage.Eden),
      "PS Survivor Space"       -> MemoryPool("PS Survivor Space", "survivor", Usage.YoungGeneration),
      "PS Old Gen"              -> MemoryPool("PS Old Gen", "old", Usage.OldGeneration)
    )

    private val _invalidChars: Regex = """[^a-z0-9]""".r

    def sanitize(name: String): String =
      _invalidChars.replaceAllIn(name.toLowerCase, "-")
  }
}
