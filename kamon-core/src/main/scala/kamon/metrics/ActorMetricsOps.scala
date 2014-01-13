/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

import org.HdrHistogram.{ AbstractHistogram, AtomicHistogram }
import kamon.util.GlobPathFilter
import scala.collection.concurrent.TrieMap
import scala.collection.JavaConversions.iterableAsScalaIterable
import akka.actor._
import kamon.metrics.ActorMetricsDispatcher.{ ActorMetricsSnapshot, FlushMetrics }
import kamon.Kamon
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import kamon.metrics.ActorMetricsDispatcher.Subscribe

trait ActorMetricsOps {
  self: ActorMetricsExtension ⇒

  val config = system.settings.config.getConfig("kamon.metrics.actors")
  val actorMetrics = TrieMap[String, HdrActorMetricsRecorder]()

  val trackedActors: Vector[GlobPathFilter] = config.getStringList("tracked").map(glob ⇒ new GlobPathFilter(glob)).toVector
  val excludedActors: Vector[GlobPathFilter] = config.getStringList("excluded").map(glob ⇒ new GlobPathFilter(glob)).toVector

  val actorMetricsFactory: () ⇒ HdrActorMetricsRecorder = {
    val settings = config.getConfig("hdr-settings")
    val processingTimeHdrConfig = HdrConfiguration.fromConfig(settings.getConfig("processing-time"))
    val timeInMailboxHdrConfig = HdrConfiguration.fromConfig(settings.getConfig("time-in-mailbox"))
    val mailboxSizeHdrConfig = HdrConfiguration.fromConfig(settings.getConfig("mailbox-size"))

    () ⇒ new HdrActorMetricsRecorder(processingTimeHdrConfig, timeInMailboxHdrConfig, mailboxSizeHdrConfig)
  }

  import scala.concurrent.duration._
  system.scheduler.schedule(0.seconds, 10.seconds)(
    actorMetrics.collect {
      case (name, recorder: HdrActorMetricsRecorder) ⇒
        println(s"Actor: $name")
        recorder.processingTimeHistogram.copy.getHistogramData.outputPercentileDistribution(System.out, 1000000D)
    })(system.dispatcher)

  def shouldTrackActor(path: String): Boolean =
    trackedActors.exists(glob ⇒ glob.accept(path)) && !excludedActors.exists(glob ⇒ glob.accept(path))

  def registerActor(path: String): HdrActorMetricsRecorder = actorMetrics.getOrElseUpdate(path, actorMetricsFactory())

  def unregisterActor(path: String): Unit = actorMetrics.remove(path)
}

class HdrActorMetricsRecorder(processingTimeHdrConfig: HdrConfiguration, timeInMailboxHdrConfig: HdrConfiguration,
                              mailboxSizeHdrConfig: HdrConfiguration) {

  val processingTimeHistogram = new AtomicHistogram(processingTimeHdrConfig.highestTrackableValue, processingTimeHdrConfig.significantValueDigits)
  val timeInMailboxHistogram = new AtomicHistogram(timeInMailboxHdrConfig.highestTrackableValue, timeInMailboxHdrConfig.significantValueDigits)
  val mailboxSizeHistogram = new AtomicHistogram(mailboxSizeHdrConfig.highestTrackableValue, mailboxSizeHdrConfig.significantValueDigits)

  def recordTimeInMailbox(waitTime: Long): Unit = timeInMailboxHistogram.recordValue(waitTime)

  def recordProcessingTime(processingTime: Long): Unit = processingTimeHistogram.recordValue(processingTime)

  def snapshot(): HdrActorMetricsSnapshot = {
    HdrActorMetricsSnapshot(processingTimeHistogram.copy(), timeInMailboxHistogram.copy(), mailboxSizeHistogram.copy())
  }

  def reset(): Unit = {
    processingTimeHistogram.reset()
    timeInMailboxHistogram.reset()
    mailboxSizeHistogram.reset()
  }
}

case class HdrActorMetricsSnapshot(processingTimeHistogram: AbstractHistogram, timeInMailboxHistogram: AbstractHistogram,
                                   mailboxSizeHistogram: AbstractHistogram)

class ActorMetricsDispatcher extends Actor {
  val tickInterval = Duration(context.system.settings.config.getNanoseconds("kamon.metrics.tick-interval"), TimeUnit.NANOSECONDS)
  val flushMetricsSchedule = context.system.scheduler.schedule(tickInterval, tickInterval, self, FlushMetrics)(context.dispatcher)

  var subscribedForever: Map[GlobPathFilter, List[ActorRef]] = Map.empty
  var subscribedForOne: Map[GlobPathFilter, List[ActorRef]] = Map.empty
  var lastTick = System.currentTimeMillis()

  def receive = {
    case Subscribe(path, true)  ⇒ subscribeForever(path, sender)
    case Subscribe(path, false) ⇒ subscribeOneOff(path, sender)
    case FlushMetrics           ⇒ flushMetrics()
  }

  def subscribeForever(path: String, receiver: ActorRef): Unit = subscribedForever = subscribe(receiver, path, subscribedForever)

  def subscribeOneOff(path: String, receiver: ActorRef): Unit = subscribedForOne = subscribe(receiver, path, subscribedForOne)

  def subscribe(receiver: ActorRef, path: String, target: Map[GlobPathFilter, List[ActorRef]]): Map[GlobPathFilter, List[ActorRef]] = {
    val pathFilter = new GlobPathFilter(path)
    val oldReceivers = target.get(pathFilter).getOrElse(Nil)
    target.updated(pathFilter, receiver :: oldReceivers)
  }

  def flushMetrics(): Unit = {
    val currentTick = System.currentTimeMillis()
    val snapshots = Kamon(ActorMetrics)(context.system).actorMetrics.map {
      case (path, metrics) ⇒
        val snapshot = metrics.snapshot()
        metrics.reset()

        (path, snapshot)
    }.toMap

    dispatchMetricsTo(subscribedForOne, snapshots, currentTick)
    dispatchMetricsTo(subscribedForever, snapshots, currentTick)

    subscribedForOne = Map.empty
    lastTick = currentTick
  }

  def dispatchMetricsTo(subscribers: Map[GlobPathFilter, List[ActorRef]], snapshots: Map[String, HdrActorMetricsSnapshot],
                        currentTick: Long): Unit = {

    for ((subscribedPath, receivers) ← subscribers) {
      val metrics = snapshots.filterKeys(snapshotPath ⇒ subscribedPath.accept(snapshotPath))
      val actorMetrics = ActorMetricsSnapshot(lastTick, currentTick, metrics)

      receivers.foreach(ref ⇒ ref ! actorMetrics)
    }
  }
}

object ActorMetricsDispatcher {
  case class Subscribe(path: String, forever: Boolean = false)
  case class UnSubscribe(path: String)

  case class ActorMetricsSnapshot(fromMillis: Long, toMillis: Long, metrics: Map[String, HdrActorMetricsSnapshot])
  case object FlushMetrics
}
