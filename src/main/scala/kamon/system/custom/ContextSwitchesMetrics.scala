/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.system.custom

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{ Paths, Files }

import akka.actor.{ Props, Actor, ActorSystem }
import akka.event.{ Logging, LoggingAdapter }
import kamon.Kamon
import kamon.metric._
import kamon.metric.instrument.InstrumentFactory
import kamon.system.custom.ContextSwitchesUpdater.UpdateContextSwitches
import org.hyperic.sigar.Sigar
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.concurrent.duration.FiniteDuration

/**
 *  Context Switches metrics:
 *    - process-voluntary: Total number of voluntary context switches related to the current process (one thread explicitly yield the CPU to another).
 *    - process-non-voluntary: Total number of involuntary context switches related to the current process (the system scheduler suspends and active thread, and switches control to a different thread).
 *    - global:  Total number of context switches across all CPUs.
 */
class ContextSwitchesMetrics(pid: Long, log: LoggingAdapter, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val perProcessVoluntary = histogram("context-switches-process-voluntary")
  val perProcessNonVoluntary = histogram("context-switches-process-non-voluntary")
  val global = histogram("context-switches-global")

  def update(): Unit = {
    def contextSwitchesByProcess(pid: Long): (Long, Long) = {
      val filename = s"/proc/$pid/status"
      var voluntaryContextSwitches = 0L
      var nonVoluntaryContextSwitches = 0L

      try {
        for (line ← Files.readAllLines(Paths.get(filename), StandardCharsets.US_ASCII).asScala.toList) {
          if (line.startsWith("voluntary_ctxt_switches")) {
            voluntaryContextSwitches = line.substring(line.indexOf(":") + 1).trim.toLong
          }
          if (line.startsWith("nonvoluntary_ctxt_switches")) {
            nonVoluntaryContextSwitches = line.substring(line.indexOf(":") + 1).trim.toLong
          }
        }
      } catch {
        case ex: IOException ⇒ log.error("Error trying to read [{}]", filename)
      }
      (voluntaryContextSwitches, nonVoluntaryContextSwitches)
    }

    def contextSwitches: Long = {
      val filename = "/proc/stat"
      var contextSwitches = 0L

      try {
        for (line ← Files.readAllLines(Paths.get(filename), StandardCharsets.US_ASCII).asScala.toList) {
          if (line.startsWith("ctxt")) {
            contextSwitches = line.substring(line.indexOf(" ") + 1).toLong
          }
        }
      } catch {
        case ex: IOException ⇒ log.error("Error trying to read [{}]", filename)
      }
      contextSwitches
    }

    val (voluntary, nonVoluntary) = contextSwitchesByProcess(pid)
    perProcessVoluntary.record(voluntary)
    perProcessNonVoluntary.record(nonVoluntary)
    global.record(contextSwitches)
  }
}

object ContextSwitchesMetrics {

  def register(system: ActorSystem, refreshInterval: FiniteDuration): ContextSwitchesMetrics = {
    val log = Logging(system, "ContextSwitchesMetrics")
    val pid = (new Sigar).getPid

    Kamon.metrics.entity(EntityRecorderFactory("system-metric", new ContextSwitchesMetrics(pid, log, _)), "context-switches")
  }
}

class ContextSwitchesUpdater(csm: ContextSwitchesMetrics, refreshInterval: FiniteDuration) extends Actor {
  val schedule = context.system.scheduler.schedule(refreshInterval, refreshInterval, self, UpdateContextSwitches)(context.dispatcher)

  def receive = {
    case UpdateContextSwitches ⇒ csm.update()
  }

  override def postStop(): Unit = {
    schedule.cancel()
    super.postStop()
  }
}

object ContextSwitchesUpdater {
  case object UpdateContextSwitches

  def props(csm: ContextSwitchesMetrics, refreshInterval: FiniteDuration): Props =
    Props(new ContextSwitchesUpdater(csm, refreshInterval))
}