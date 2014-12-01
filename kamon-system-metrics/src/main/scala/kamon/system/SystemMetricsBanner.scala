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

import akka.actor.ActorLogging
import org.hyperic.sigar._

import scala.util.control.NoStackTrace

trait SystemMetricsBanner {
  self: ActorLogging ⇒

  def printBanner(sigar: Sigar) = {
    val os = OperatingSystem.getInstance

    def loadAverage(sigar: Sigar) = try {
      val average = sigar.getLoadAverage
      (average(0), average(1), average(2))
    } catch {
      case s: org.hyperic.sigar.SigarNotImplementedException ⇒ (0d, 0d, 0d)
    }

    def uptime(sigar: Sigar) = {
      def formatUptime(uptime: Double): String = {
        var retval: String = ""
        val days: Int = uptime.toInt / (60 * 60 * 24)
        var minutes: Int = 0
        var hours: Int = 0

        if (days != 0) {
          retval += s"$days ${if (days > 1) "days" else "day"}, "
        }

        minutes = uptime.toInt / 60
        hours = minutes / 60
        hours %= 24
        minutes %= 60

        if (hours != 0) {
          retval += hours + ":" + minutes
        } else {
          retval += minutes + " min"
        }
        retval
      }

      val uptime = sigar.getUptime
      val now = System.currentTimeMillis()

      s"up ${formatUptime(uptime.getUptime)}"
    }

    val message =
      """
        |
        |  _____           _                 __  __      _        _          _                     _          _
        | / ____|         | |               |  \/  |    | |      (_)        | |                   | |        | |
        || (___  _   _ ___| |_ ___ _ __ ___ | \  / | ___| |_ _ __ _  ___ ___| |     ___   __ _  __| | ___  __| |
        | \___ \| | | / __| __/ _ \ '_ ` _ \| |\/| |/ _ \ __| '__| |/ __/ __| |    / _ \ / _` |/ _` |/ _ \/ _` |
        | ____) | |_| \__ \ ||  __/ | | | | | |  | |  __/ |_| |  | | (__\__ \ |___| (_) | (_| | (_| |  __/ (_| |
        ||_____/ \__, |___/\__\___|_| |_| |_|_|  |_|\___|\__|_|  |_|\___|___/______\___/ \__,_|\__,_|\___|\__,_|
        |         __/ |
        |        |___/
        |
        |              [System Status]                                                    [OS Information]
        |     |--------------------------------|                             |----------------------------------------|
        |            Up Time: %-10s                                           Description: %s
        |       Load Average: %-16s                                            Name: %s
        |                                                                              Version: %s
        |                                                                                 Arch: %s
        |
      """.stripMargin.format(uptime(sigar), os.getDescription, loadAverage(sigar), os.getName, os.getVersion, os.getArch)
    log.info(message)
  }

  class UnexpectedSigarException(message: String) extends RuntimeException(message) with NoStackTrace
}
