/*
 * =========================================================================================
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

package kamon.system

import java.lang.management.GarbageCollectorMXBean

import akka.actor.{ Actor, Props }
import kamon.metrics.GCMetrics.GCMetricRecorder

import scala.concurrent.duration.FiniteDuration

class GcMetricsCollector(collectInterval: FiniteDuration, recorder: Option[GCMetricRecorder], extractor: GcMetricExtractor) extends Actor {
  import kamon.system.GcMetricsCollector._

  val collectSchedule = context.system.scheduler.schedule(collectInterval, collectInterval, self, Collect)(SystemMetrics(context.system).dispatcher)

  def receive: Receive = {
    case Collect ⇒ collectMetrics()
  }

  override def postStop() = collectSchedule.cancel()

  def collectMetrics(): Unit = recorder.map(recordGc)

  private def recordGc(gcr: GCMetricRecorder) = {
    val gcMeasure = extractor.extract()

    gcr.count.record(gcMeasure.collectionCount)
    gcr.time.record(gcMeasure.collectionTime)
  }
}

object GcMetricsCollector {
  case object Collect

  def props(collectInterval: FiniteDuration, recorder: Option[GCMetricRecorder], extractor: GcMetricExtractor): Props = Props(classOf[GcMetricsCollector], collectInterval, recorder, extractor)
}

case class GcMeasure(collectionCount: Long, collectionTime: Long)

case class GcMetricExtractor(gc: GarbageCollectorMXBean) {
  var previousGcCount = 0L
  var previousGcTime = 0L

  def extract(): GcMeasure = {
    var diffCollectionCount = 0L
    var diffCollectionTime = 0L

    val collectionCount = gc.getCollectionCount
    val collectionTime = gc.getCollectionTime

    if (collectionCount > 0)
      diffCollectionCount = collectionCount - previousGcCount

    if (collectionTime > 0)
      diffCollectionTime = collectionTime - previousGcTime

    previousGcCount = collectionCount
    previousGcTime = collectionTime

    GcMeasure(diffCollectionCount, diffCollectionTime)
  }
}