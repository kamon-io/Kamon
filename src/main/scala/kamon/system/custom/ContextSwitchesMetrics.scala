/*
 * =========================================================================================
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

package kamon.system.custom

import java.io.IOException
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.file.{Files, Paths}

import kamon.Kamon
import kamon.system.{CustomMetricBuilder, Metric, MetricBuilder}
import org.slf4j.Logger

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

/**
 *  Context Switches metrics:
 *    - process-voluntary: Total number of voluntary context switches related to the current process (one thread explicitly yield the CPU to another).
 *    - process-non-voluntary: Total number of involuntary context switches related to the current process (the system scheduler suspends and active thread, and switches control to a different thread).
 *    - global:  Total number of context switches across all CPUs.
 */
object ContextSwitchesMetrics extends MetricBuilder("context-switches") with CustomMetricBuilder {
  def build(pid: Long, metricPrefix: String, logger: Logger)  = new Metric {

    val perProcessVoluntaryMetric     = Kamon.histogram(s"$metricPrefix.process-voluntary")
    val perProcessNonVoluntaryMetric  = Kamon.histogram(s"$metricPrefix.process-non-voluntary")
    val globalMetric                  = Kamon.histogram(s"$metricPrefix.global")

    override def update(): Unit = {
      val (voluntary, nonVoluntary) = contextSwitchesByProcess(pid)
      perProcessVoluntaryMetric.record(voluntary)
      perProcessNonVoluntaryMetric.record(nonVoluntary)
      globalMetric.record(contextSwitches)
    }

    def contextSwitchesByProcess(pid: Long): (Long, Long) = {
      val filename = s"/proc/$pid/status"
      var voluntaryContextSwitches = 0L
      var nonVoluntaryContextSwitches = 0L

      try {
        for (line ← Files.readAllLines(Paths.get(filename), US_ASCII).asScala.toList) {
          if (line.startsWith("voluntary_ctxt_switches")) {
            voluntaryContextSwitches = line.substring(line.indexOf(":") + 1).trim.toLong
          }
          if (line.startsWith("nonvoluntary_ctxt_switches")) {
            nonVoluntaryContextSwitches = line.substring(line.indexOf(":") + 1).trim.toLong
          }
        }
      } catch {
        case _: IOException ⇒ logger.error("Error trying to read [{}]", filename)
      }
      (voluntaryContextSwitches, nonVoluntaryContextSwitches)
    }

    def contextSwitches: Long = {
      val filename = "/proc/stat"
      var contextSwitches = 0L

      try {
        for (line ← Files.readAllLines(Paths.get(filename), US_ASCII).asScala.toList) {
          if (line.startsWith("ctxt")) {
            contextSwitches = line.substring(line.indexOf(" ") + 1).toLong
          }
        }
      } catch {
        case _: IOException ⇒ logger.error("Error trying to read [{}]", filename)
      }
      contextSwitches
    }

  }
}