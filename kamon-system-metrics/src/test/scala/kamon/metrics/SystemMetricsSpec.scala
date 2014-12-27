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

import akka.actor.ActorSystem
import akka.testkit.{ TestKitBase, TestProbe }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metrics.CPUMetrics.CPUMetricSnapshot
import kamon.metrics.ClassLoadingMetrics.ClassLoadingMetricSnapshot
import kamon.metrics.ContextSwitchesMetrics.ContextSwitchesMetricsSnapshot
import kamon.metrics.DiskMetrics.DiskMetricsSnapshot
import kamon.metrics.GCMetrics.GCMetricSnapshot
import kamon.metrics.HeapMetrics.HeapMetricSnapshot
import kamon.metrics.LoadAverageMetrics.LoadAverageMetricsSnapshot
import kamon.metrics.MemoryMetrics.MemoryMetricSnapshot
import kamon.metrics.NetworkMetrics.NetworkMetricSnapshot
import kamon.metrics.NonHeapMetrics.NonHeapMetricSnapshot
import kamon.metrics.ProcessCPUMetrics.ProcessCPUMetricsSnapshot
import kamon.metrics.ThreadMetrics.ThreadMetricSnapshot
import kamon.metrics._
import kamon.system.SystemMetricsExtension
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._

class SystemMetricsSpec extends TestKitBase with WordSpecLike with Matchers with RedirectLogging {
  implicit lazy val system: ActorSystem = ActorSystem("system-metrics-spec", ConfigFactory.parseString(
    """
      |akka {
      |  extensions = ["kamon.system.SystemMetrics"]
      |}
      |
      |kamon.metrics {
      |  disable-aspectj-weaver-missing-error = true
      |  tick-interval = 1 second
      |}
    """.stripMargin))

  "the Kamon  CPU Metrics" should {
    "record user, system, wait, idle, stolen metrics" in new CPUMetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val CPUMetrics = expectCPUMetrics(metricsListener, 3 seconds)
      CPUMetrics.user.max should be >= 0L
      CPUMetrics.system.max should be >= 0L
      CPUMetrics.cpuWait.max should be >= 0L
      CPUMetrics.idle.max should be >= 0L
      CPUMetrics.stolen.max should be >= 0L
    }
  }
  "the Kamon GC Metrics" should {
    "record count, time metrics" in new GCMetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val GCMetrics = expectGCMetrics(metricsListener, 3 seconds)
      GCMetrics.count.max should be >= 0L
      GCMetrics.time.max should be >= 0L
    }
  }

  "the Kamon Heap Metrics" should {
    "record used, max, commited metrics" in new HeapMetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val HeapMetrics = expectHeapMetrics(metricsListener, 3 seconds)
      HeapMetrics.used.max should be >= 0L
      HeapMetrics.max.max should be >= 0L
      HeapMetrics.committed.max should be >= 0L
    }
  }

  "the Kamon Non-Heap Metrics" should {
    "record used, max, commited metrics" in new NonHeapMetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val NonHeapMetrics = expectNonHeapMetrics(metricsListener, 3 seconds)
      NonHeapMetrics.used.max should be >= 0L
      NonHeapMetrics.max.max should be >= 0L
      NonHeapMetrics.committed.max should be >= 0L
    }
  }

  "the Kamon Thread Metrics" should {
    "record daemon, count, peak metrics" in new ThreadMetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val ThreadMetrics = expectThreadMetrics(metricsListener, 3 seconds)
      ThreadMetrics.daemon.max should be >= 0L
      ThreadMetrics.count.max should be >= 0L
      ThreadMetrics.peak.max should be >= 0L
    }
  }

  "the Kamon ClassLoading Metrics" should {
    "record loaded, unloaded, current metrics" in new ClassLoadingMetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val ClassLoadingMetrics = expectClassLoadingMetrics(metricsListener, 3 seconds)
      ClassLoadingMetrics.loaded.max should be >= 0L
      ClassLoadingMetrics.unloaded.max should be >= 0L
      ClassLoadingMetrics.current.max should be >= 0L
    }
  }

  "the Kamon Disk Metrics" should {
    "record reads, writes, queue, service time metrics" in new DiskMetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val DiskMetrics = expectDiskMetrics(metricsListener, 3 seconds)
      DiskMetrics.reads.max should be >= 0L
      DiskMetrics.writes.max should be >= 0L
      DiskMetrics.queue.max should be >= 0L
      DiskMetrics.serviceTime.max should be >= 0L
    }
  }

  "the Kamon Load Average Metrics" should {
    "record 1 minute, 5 minutes and 15 minutes metrics" in new LoadAverageMetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val LoadAverageMetrics = expectLoadAverageMetrics(metricsListener, 3 seconds)
      LoadAverageMetrics.one.max should be >= 0L
      LoadAverageMetrics.five.max should be >= 0L
      LoadAverageMetrics.fifteen.max should be >= 0L
    }
  }

  "the Kamon Memory Metrics" should {
    "record used, free, buffer, cache, swap used, swap free metrics" in new MemoryMetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val MemoryMetrics = expectMemoryMetrics(metricsListener, 3 seconds)
      MemoryMetrics.used.max should be >= 0L
      MemoryMetrics.free.max should be >= 0L
      MemoryMetrics.buffer.max should be >= 0L
      MemoryMetrics.cache.max should be >= 0L
      MemoryMetrics.swapUsed.max should be >= 0L
      MemoryMetrics.swapFree.max should be >= 0L
    }
  }

  "the Kamon Network Metrics" should {
    "record rxBytes, txBytes, rxErrors, txErrors, rxDropped, txDropped metrics" in new NetworkMetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val NetworkMetrics = expectNetworkMetrics(metricsListener, 3 seconds)
      NetworkMetrics.rxBytes.max should be >= 0L
      NetworkMetrics.txBytes.max should be >= 0L
      NetworkMetrics.rxErrors.max should be >= 0L
      NetworkMetrics.txErrors.max should be >= 0L
      NetworkMetrics.rxDropped.max should be >= 0L
      NetworkMetrics.txDropped.max should be >= 0L
    }
  }

  "the Kamon Process CPU Metrics" should {
    "record Cpu Percent, Total Process Time metrics" in new ProcessCPUMetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val ProcessCPUMetrics = expectProcessCPUMetrics(metricsListener, 3 seconds)
      ProcessCPUMetrics.cpuPercent.max should be >= 0L
      ProcessCPUMetrics.totalProcessTime.max should be >= 0L
    }
  }

  "the Kamon ContextSwitches Metrics" should {
    "record Context Switches Global, Voluntary and Non Voluntary metrics" in new ContextSwitchesMetricsListenerFixture {
      val metricsListener = subscribeToMetrics()

      val ContextSwitchesMetrics = expectContextSwitchesMetrics(metricsListener, 3 seconds)
      ContextSwitchesMetrics.perProcessVoluntary.max should be >= 0L
      ContextSwitchesMetrics.perProcessNonVoluntary.max should be >= 0L
      ContextSwitchesMetrics.global.max should be >= 0L
    }
  }

  def expectCPUMetrics(listener: TestProbe, waitTime: FiniteDuration): CPUMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val cpuMetricsOption = tickSnapshot.metrics.get(CPUMetrics(SystemMetricsExtension.CPU))
    cpuMetricsOption should not be empty
    cpuMetricsOption.get.asInstanceOf[CPUMetricSnapshot]
  }

  trait CPUMetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(CPUMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }

  def expectGCMetrics(listener: TestProbe, waitTime: FiniteDuration): GCMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }

    val gcMetricsOption = tickSnapshot.metrics.get(GCMetrics(SystemMetricsExtension.garbageCollectors(0).getName.replaceAll("""[^\w]""", "-")))
    gcMetricsOption should not be empty
    gcMetricsOption.get.asInstanceOf[GCMetricSnapshot]
  }

  trait GCMetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(GCMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }

  def expectHeapMetrics(listener: TestProbe, waitTime: FiniteDuration): HeapMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val heapMetricsOption = tickSnapshot.metrics.get(HeapMetrics(SystemMetricsExtension.Heap))
    heapMetricsOption should not be empty
    heapMetricsOption.get.asInstanceOf[HeapMetricSnapshot]
  }

  trait HeapMetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(HeapMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }

  trait NonHeapMetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(NonHeapMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }

  def expectNonHeapMetrics(listener: TestProbe, waitTime: FiniteDuration): NonHeapMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val nonHeapMetricsOption = tickSnapshot.metrics.get(NonHeapMetrics(SystemMetricsExtension.NonHeap))
    nonHeapMetricsOption should not be empty
    nonHeapMetricsOption.get.asInstanceOf[NonHeapMetricSnapshot]
  }

  trait ThreadMetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(ThreadMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }

  def expectThreadMetrics(listener: TestProbe, waitTime: FiniteDuration): ThreadMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val threadMetricsOption = tickSnapshot.metrics.get(ThreadMetrics(SystemMetricsExtension.Threads))
    threadMetricsOption should not be empty
    threadMetricsOption.get.asInstanceOf[ThreadMetricSnapshot]
  }

  trait ClassLoadingMetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(ClassLoadingMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }

  def expectClassLoadingMetrics(listener: TestProbe, waitTime: FiniteDuration): ClassLoadingMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val classLoadingMetricsOption = tickSnapshot.metrics.get(ClassLoadingMetrics(SystemMetricsExtension.Classes))
    classLoadingMetricsOption should not be empty
    classLoadingMetricsOption.get.asInstanceOf[ClassLoadingMetricSnapshot]
  }

  trait DiskMetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(DiskMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }

  def expectDiskMetrics(listener: TestProbe, waitTime: FiniteDuration): DiskMetricsSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val diskMetricsOption = tickSnapshot.metrics.get(DiskMetrics(SystemMetricsExtension.Disk))
    diskMetricsOption should not be empty
    diskMetricsOption.get.asInstanceOf[DiskMetricsSnapshot]
  }

  trait LoadAverageMetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(LoadAverageMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }

  def expectLoadAverageMetrics(listener: TestProbe, waitTime: FiniteDuration): LoadAverageMetricsSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val loadAverageMetricsOption = tickSnapshot.metrics.get(LoadAverageMetrics(SystemMetricsExtension.LoadAverage))
    loadAverageMetricsOption should not be empty
    loadAverageMetricsOption.get.asInstanceOf[LoadAverageMetricsSnapshot]
  }

  def expectMemoryMetrics(listener: TestProbe, waitTime: FiniteDuration): MemoryMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val memoryMetricsOption = tickSnapshot.metrics.get(MemoryMetrics(SystemMetricsExtension.Memory))
    memoryMetricsOption should not be empty
    memoryMetricsOption.get.asInstanceOf[MemoryMetricSnapshot]
  }

  trait MemoryMetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(MemoryMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }

  def expectNetworkMetrics(listener: TestProbe, waitTime: FiniteDuration): NetworkMetricSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val networkMetricsOption = tickSnapshot.metrics.get(NetworkMetrics(SystemMetricsExtension.Network))
    networkMetricsOption should not be empty
    networkMetricsOption.get.asInstanceOf[NetworkMetricSnapshot]
  }

  trait NetworkMetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(NetworkMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }

  def expectProcessCPUMetrics(listener: TestProbe, waitTime: FiniteDuration): ProcessCPUMetricsSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val processCPUMetricsOption = tickSnapshot.metrics.get(ProcessCPUMetrics(SystemMetricsExtension.ProcessCPU))
    processCPUMetricsOption should not be empty
    processCPUMetricsOption.get.asInstanceOf[ProcessCPUMetricsSnapshot]
  }

  trait ProcessCPUMetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(ProcessCPUMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }

  def expectContextSwitchesMetrics(listener: TestProbe, waitTime: FiniteDuration): ContextSwitchesMetricsSnapshot = {
    val tickSnapshot = within(waitTime) {
      listener.expectMsgType[TickMetricSnapshot]
    }
    val contextSwitchesMetricsOption = tickSnapshot.metrics.get(ContextSwitchesMetrics(SystemMetricsExtension.ContextSwitches))
    contextSwitchesMetricsOption should not be empty
    contextSwitchesMetricsOption.get.asInstanceOf[ContextSwitchesMetricsSnapshot]
  }

  trait ContextSwitchesMetricsListenerFixture {
    def subscribeToMetrics(): TestProbe = {
      val metricsListener = TestProbe()
      Kamon(Metrics).subscribe(ContextSwitchesMetrics, "*", metricsListener.ref, permanently = true)
      // Wait for one empty snapshot before proceeding to the test.
      metricsListener.expectMsgType[TickMetricSnapshot]
      metricsListener
    }
  }
}
