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

import java.lang.management.{BufferPoolMXBean, ManagementFactory, MemoryUsage}
import com.sun.management.{ThreadMXBean => SunThreadMXBean}
import com.sun.management.GarbageCollectionNotificationInfo
import com.sun.management.GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION
import com.typesafe.config.Config

import javax.management.openmbean.CompositeData
import javax.management.{Notification, NotificationEmitter, NotificationListener}
import kamon.instrumentation.system.jvm.JvmMetrics.{
  ClassLoadingInstruments,
  GarbageCollectionInstruments,
  MemoryUsageInstruments,
  ThreadsInstruments
}
import kamon.instrumentation.system.jvm.JvmMetricsCollector.{Collector, MemoryPool, ThreadState}
import kamon.module.{Module, ModuleFactory, ScheduledAction}
import kamon.tag.TagSet

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, mapAsScalaMapConverter}
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.util.matching.Regex

class JvmMetricsCollector(ec: ExecutionContext) extends ScheduledAction {
  private val _defaultTags = TagSet.of("component", "jvm")
  private val _gcListener = registerGcListener(_defaultTags)
  private val _memoryUsageInstruments = new MemoryUsageInstruments(_defaultTags)
  private val _threadsUsageInstruments = new ThreadsInstruments()
  private val _classLoadingInstruments = new ClassLoadingInstruments(_defaultTags)
  private val _jmxCollectorTask =
    new JmxMetricsCollectorTask(_memoryUsageInstruments, _threadsUsageInstruments, _classLoadingInstruments)

  override def run(): Unit = {
    _jmxCollectorTask.run()
  }

  override def stop(): Unit = {
    deregisterGcListener()
  }

  override def reconfigure(newConfig: Config): Unit = {}

  private def registerGcListener(defaultTags: TagSet): NotificationListener = {
    val gcInstruments = new GarbageCollectionInstruments(defaultTags)
    val gcListener = new GcNotificationListener(gcInstruments)

    ManagementFactory.getGarbageCollectorMXBeans().asScala.foreach(gcBean => {
      if (gcBean.isInstanceOf[NotificationEmitter])
        gcBean.asInstanceOf[NotificationEmitter].addNotificationListener(gcListener, null, null)
    })

    gcListener
  }

  private def deregisterGcListener(): Unit = {
    ManagementFactory.getGarbageCollectorMXBeans().asScala.foreach(gcBean => {
      if (gcBean.isInstanceOf[NotificationEmitter]) {
        gcBean.asInstanceOf[NotificationEmitter].removeNotificationListener(_gcListener)
      }
    })
  }

  class GcNotificationListener(val gcInstruments: GarbageCollectionInstruments) extends NotificationListener {
    override def handleNotification(notification: Notification, handback: Any): Unit = {
      if (notification.getType() == GARBAGE_COLLECTION_NOTIFICATION) {
        val compositeData = notification.getUserData.asInstanceOf[CompositeData]
        val info = GarbageCollectionNotificationInfo.from(compositeData)
        val collector = Collector.find(info.getGcName)

        // This prevents recording the ZGC/Shenandoah cycles
        if (collector.pausesTheJVM) {
          gcInstruments.garbageCollectionTime(collector).record(info.getGcInfo.getDuration)
        }

        val usageBeforeGc = info.getGcInfo.getMemoryUsageBeforeGc.asScala
        val usageAfterGc = info.getGcInfo.getMemoryUsageAfterGc.asScala

        usageBeforeGc.foreach {
          case (regionName, regionUsageBeforeGc) =>
            val region = MemoryPool.find(regionName)

            // We assume that if the old generation grew during this GC event then some data was promoted to it and will
            // record it as promotion to the old generation.
            if (region.usage == MemoryPool.Usage.OldGeneration) {
              val regionUsageAfterGc = usageAfterGc(regionName)
              val diff = regionUsageAfterGc.getUsed - regionUsageBeforeGc.getUsed

              if (diff > 0)
                gcInstruments.promotionToOld.record(diff)

            }
        }

        var totalMemory = 0L
        var usedMemory = 0L
        usageAfterGc.foreach {
          case (regionName, regionUsageAfterGC) =>
            val region = MemoryPool.find(regionName)

            if (
              regionUsageAfterGC.getMax != -1 && (region.usage == MemoryPool.Usage.OldGeneration || region.usage == MemoryPool.Usage.SingleGeneration)
            ) {
              totalMemory += regionUsageAfterGC.getMax
              usedMemory += regionUsageAfterGC.getUsed
            }
        }

        if (totalMemory > 0L) {
          val utilizationPercentage = Math.min(100d, (usedMemory.toDouble * 100d) / totalMemory.toDouble).toLong
          gcInstruments.memoryUtilization.record(utilizationPercentage)
        }
      }
    }
  }

  class JmxMetricsCollectorTask(
    memoryUsageInstruments: MemoryUsageInstruments,
    threadsInstruments: ThreadsInstruments,
    classLoadingInstruments: ClassLoadingInstruments
  ) extends Runnable {

    private val _heapUsage = memoryUsageInstruments.regionInstruments("heap")
    private val _nonHeapUsage = memoryUsageInstruments.regionInstruments("non-heap")
    private val _classLoading = classLoadingInstruments
    private var _lastSeenAllocatedBytes = 0L

    override def run(): Unit = {
      val threadsMxBean = ManagementFactory.getThreadMXBean().asInstanceOf[SunThreadMXBean]
      threadsInstruments.total.update(threadsMxBean.getThreadCount())
      threadsInstruments.peak.update(threadsMxBean.getPeakThreadCount())
      threadsInstruments.daemon.update(threadsMxBean.getDaemonThreadCount())

      val allThreadIds = threadsMxBean.getAllThreadIds()
      allThreadIds.map(threadsMxBean.getThreadInfo(_, 0))
        .groupBy(_.getThreadState)
        .mapValues(_.length)
        .foreach {
          case (state, count) =>
            val threadState = ThreadState.find(state.toString)
            threadsInstruments.threadState(threadState).update(count)
        }

      val totalAllocatedBytes = threadsMxBean.getThreadAllocatedBytes(allThreadIds).sum
      val bytesAllocatedSinceLastCheck = totalAllocatedBytes - _lastSeenAllocatedBytes
      if (bytesAllocatedSinceLastCheck > 0)
        memoryUsageInstruments.allocation.increment(bytesAllocatedSinceLastCheck)
      _lastSeenAllocatedBytes = totalAllocatedBytes

      val currentHeapUsage = ManagementFactory.getMemoryMXBean.getHeapMemoryUsage
      val freeHeap = Math.max(0L, currentHeapUsage.getMax - currentHeapUsage.getUsed)
      _heapUsage.free.record(freeHeap)
      _heapUsage.used.record(currentHeapUsage.getUsed)
      _heapUsage.max.update(currentHeapUsage.getMax)
      _heapUsage.committed.update(currentHeapUsage.getCommitted)

      val currentNonHeapUsage = ManagementFactory.getMemoryMXBean.getNonHeapMemoryUsage
      val freeNonHeap = Math.max(0L, currentNonHeapUsage.getMax - currentNonHeapUsage.getUsed)
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
        val freeMemory = Math.max(0L, memoryUsage.getMax - memoryUsage.getUsed)

        poolInstruments.free.record(freeMemory)
        poolInstruments.used.record(memoryUsage.getUsed)
        poolInstruments.max.update(memoryUsage.getMax)
        poolInstruments.committed.update(memoryUsage.getCommitted)
      })

      ManagementFactory.getPlatformMXBeans(classOf[BufferPoolMXBean]).asScala.toList.map { bean =>
        val bufferPoolInstruments = memoryUsageInstruments.bufferPoolInstruments(
          MemoryPool.sanitize(bean.getName)
        )

        bufferPoolInstruments.count.update(bean.getCount)
        bufferPoolInstruments.used.update(bean.getMemoryUsed)
        bufferPoolInstruments.capacity.update(bean.getTotalCapacity)
      }
    }
  }
}

object JvmMetricsCollector {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new JvmMetricsCollector(settings.executionContext)
  }

  case class Collector(
    name: String,
    alias: String,
    generation: Collector.Generation,
    pausesTheJVM: Boolean = true
  )

  object Collector {

    sealed trait Generation
    object Generation {
      case object Young extends Generation { override def toString: String = "young" }
      case object Old extends Generation { override def toString: String = "old" }
      case object Single extends Generation { override def toString: String = "single" }
      case object Unknown extends Generation { override def toString: String = "unknown" }
    }

    def find(collectorName: String): Collector =
      _collectorMappings.get(collectorName).getOrElse(
        Collector(collectorName, sanitizeCollectorName(collectorName), Collector.Generation.Unknown)
      )

    private val _collectorMappings: Map[String, Collector] = Map(
      "Copy" -> Collector("Copy", "copy", Generation.Young),
      "ParNew" -> Collector("ParNew", "par-new", Generation.Young),
      "MarkSweepCompact" -> Collector("MarkSweepCompact", "mark-sweep-compact", Generation.Old),
      "ConcurrentMarkSweep" -> Collector("ConcurrentMarkSweep", "concurrent-mark-sweep", Generation.Old),
      "PS Scavenge" -> Collector("PS Scavenge", "ps-scavenge", Generation.Young),
      "PS MarkSweep" -> Collector("PS MarkSweep", "ps-mark-sweep", Generation.Old),
      "G1 Young Generation" -> Collector("G1 Young Generation", "g1-young", Generation.Young),
      "G1 Old Generation" -> Collector("G1 Old Generation", "g1-old", Generation.Old),
      "ZGC Pauses" -> Collector("ZGC Pauses", "zgc-pauses", Generation.Single),
      "ZGC Cycles" -> Collector("ZGC Cycles", "zgc-cycles", Generation.Single, false),
      "Shenandoah Pauses" -> Collector("Shenandoah Pauses", "shenandoah-pauses", Generation.Single),
      "Shenandoah Cycles" -> Collector("Shenandoah Cycles", "shenandoah-cycles", Generation.Single, false)
    )

    private def sanitizeCollectorName(name: String): String =
      name.replaceAll("""[^\w]""", "-").toLowerCase
  }

  case class MemoryPool(
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
      case object SingleGeneration extends Usage
      case object Unknown extends Usage
    }

    def find(poolName: String): MemoryPool =
      _memoryRegionMappings.getOrElse(poolName, MemoryPool(poolName, sanitize(poolName), inferPoolUsage(poolName)))

    private def inferPoolUsage(poolName: String): MemoryPool.Usage = {
      val lowerCaseName = poolName.toLowerCase()

      if (lowerCaseName.endsWith("eden space"))
        Usage.Eden
      else if (lowerCaseName.contains("survivor"))
        Usage.YoungGeneration
      else if (lowerCaseName.endsWith("old gen") || lowerCaseName.endsWith("tenured gen"))
        Usage.OldGeneration
      else
        Usage.Unknown
    }

    private val _memoryRegionMappings: TrieMap[String, MemoryPool] = TrieMap(
      "Metaspace" -> MemoryPool("Metaspace", "metaspace", Usage.Metaspace),
      "Code Cache" -> MemoryPool("Code Cache", "code-cache", Usage.CodeCache),
      "CodeHeap 'profiled nmethods'" -> MemoryPool("Code Heap", "code-heap-profiled-nmethods", Usage.CodeCache),
      "CodeHeap 'non-profiled nmethods'" -> MemoryPool(
        "Code Cache",
        "code-heap-non-profiled-nmethods",
        Usage.CodeCache
      ),
      "CodeHeap 'non-nmethods'" -> MemoryPool("Code Cache", "code-heap-non-nmethods", Usage.CodeCache),
      "Compressed Class Space" -> MemoryPool("Compressed Class Space", "compressed-class-space", Usage.CodeCache),
      "Eden Space" -> MemoryPool("Eden Space", "eden", Usage.Eden),
      "PS Eden Space" -> MemoryPool("PS Eden Space", "eden", Usage.Eden),
      "PS Survivor Space" -> MemoryPool("PS Survivor Space", "survivor", Usage.YoungGeneration),
      "PS Old Gen" -> MemoryPool("PS Old Gen", "old", Usage.OldGeneration),
      "Survivor Space" -> MemoryPool("Survivor Space", "survivor-space", Usage.YoungGeneration),
      "Tenured Gen" -> MemoryPool("Tenured Gen", "tenured-gen", Usage.OldGeneration),
      "ZHeap" -> MemoryPool("ZGZ Heap", "zheap", Usage.SingleGeneration),
      "Shenandoah" -> MemoryPool("ZGZ Heap", "shenandoah", Usage.SingleGeneration)
    )

    private val _invalidChars: Regex = """[^a-z0-9]""".r

    def sanitize(name: String): String =
      _invalidChars.replaceAllIn(name.toLowerCase, "-")
  }

  sealed trait ThreadState
  object ThreadState {
    case object New extends ThreadState { override def toString: String = "new" }
    case object Runnable extends ThreadState { override def toString: String = "runnable" }
    case object Blocked extends ThreadState { override def toString: String = "blocked" }
    case object Waiting extends ThreadState { override def toString: String = "waiting" }
    case object TimedWaiting extends ThreadState { override def toString: String = "timed-waiting" }
    case object Terminated extends ThreadState { override def toString: String = "terminated" }
    case object Unknown extends ThreadState { override def toString: String = "unknown" }

    def find(state: String): ThreadState =
      _threadStateMapping.getOrElse(state, Unknown)

    private val _threadStateMapping: Map[String, ThreadState] = Map(
      "NEW" -> New,
      "RUNNABLE" -> Runnable,
      "BLOCKED" -> Blocked,
      "WAITING" -> Waiting,
      "TIMED_WAITING" -> TimedWaiting,
      "TERMINATED" -> Terminated
    )
  }
}
